package com.splitledger.budget;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.budget.dto.BudgetAmountRequest;
import com.splitledger.budget.dto.BudgetRequest;
import com.splitledger.budget.dto.BudgetResponse;
import com.splitledger.budget.dto.BudgetStatus;
import com.splitledger.category.Category;
import com.splitledger.category.CategoryService;
import com.splitledger.common.ApiException;
import com.splitledger.expense.ExpenseFilter;
import com.splitledger.expense.ExpenseService;
import com.splitledger.expense.dto.ExpenseSummary;
import com.splitledger.expense.dto.ExpenseSummary.CategoryTotal;
import com.splitledger.user.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Recurring monthly limits on personal spending. Spending isn't stored on the budget: it's the
 * same per-category summary the expenses page uses, computed for the requested month.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    /** The overall budget first, then by category name. */
    private static final Comparator<Budget> DISPLAY_ORDER = Comparator
            .comparing((Budget b) -> !b.isOverall())
            .thenComparing(b -> b.isOverall() ? "" : b.getCategory().getName(), String.CASE_INSENSITIVE_ORDER);

    private final BudgetRepository budgetRepository;
    private final CategoryService categoryService;
    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<BudgetStatus> statuses(UUID userId, YearMonth month) {
        List<Budget> budgets = budgetRepository.findByOwnerId(userId);
        if (budgets.isEmpty()) {
            return List.of();
        }
        ExpenseSummary summary = expenseService.summarize(
                userId, new ExpenseFilter(month.atDay(1), month.atEndOfMonth(), null, null));
        Map<UUID, BigDecimal> spentByCategory = summary.byCategory().stream()
                .filter(row -> row.categoryId() != null)
                .collect(Collectors.toMap(CategoryTotal::categoryId, CategoryTotal::total));
        BigDecimal spentOverall = summary.total().setScale(2);

        return budgets.stream()
                .sorted(DISPLAY_ORDER)
                .map(b -> BudgetStatus.of(b, b.isOverall()
                        ? spentOverall
                        : spentByCategory.getOrDefault(b.getCategory().getId(), ZERO)))
                .toList();
    }

    @Transactional
    public BudgetResponse create(UUID userId, BudgetRequest request) {
        Category category = request.categoryId() == null ? null : categoryService.loadAssignable(userId, request.categoryId());
        boolean exists = budgetRepository.findByOwnerId(userId).stream()
                .anyMatch(b -> Objects.equals(categoryId(b), request.categoryId()));
        if (exists) {
            throw duplicate(category);
        }
        try {
            Budget budget = new Budget(userRepository.getReferenceById(userId), category, request.amount());
            return BudgetResponse.from(budgetRepository.saveAndFlush(budget));
        } catch (DataIntegrityViolationException e) {
            // Two requests created the same budget at once; the unique constraint caught the second.
            throw duplicate(category);
        }
    }

    @Transactional
    public BudgetResponse update(UUID userId, UUID budgetId, BudgetAmountRequest request) {
        Budget budget = loadOwned(userId, budgetId);
        budget.setAmount(request.amount());
        return BudgetResponse.from(budget);
    }

    @Transactional
    public void delete(UUID userId, UUID budgetId) {
        budgetRepository.delete(loadOwned(userId, budgetId));
    }

    private Budget loadOwned(UUID userId, UUID budgetId) {
        return budgetRepository.findByIdAndOwnerId(budgetId, userId)
                .orElseThrow(() -> ApiException.notFound("Budget not found"));
    }

    private static UUID categoryId(Budget budget) {
        return budget.isOverall() ? null : budget.getCategory().getId();
    }

    private static ApiException duplicate(Category category) {
        return ApiException.conflict(category == null
                ? "You already have an overall budget"
                : "You already have a budget for " + category.getName());
    }
}
