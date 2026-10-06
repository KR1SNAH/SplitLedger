package com.splitledger.expense;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.splitledger.category.Category;
import com.splitledger.category.CategoryService;
import com.splitledger.common.ApiException;
import com.splitledger.common.PageResponse;
import com.splitledger.expense.dto.ExpenseRequest;
import com.splitledger.expense.dto.ExpenseResponse;
import com.splitledger.expense.dto.ExpenseSummary;
import com.splitledger.expense.dto.ExpenseSummary.CategoryTotal;
import com.splitledger.expense.dto.MonthTotal;
import com.splitledger.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    /** Client-facing sort keys mapped to entity properties; anything else is rejected. */
    private static final Map<String, String> SORTABLE = Map.of(
            "date", "expenseDate",
            "amount", "amount",
            "description", "description",
            "created", "createdAt");

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final ExpenseRepository expenseRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(UUID userId, ExpenseFilter filter, int page, int size, String sort) {
        Specification<Expense> spec = (root, query, cb) -> filter.toPredicate(userId, root, cb);
        var pageable = PageRequest.of(page, size, parseSort(sort));
        return PageResponse.from(expenseRepository.findAll(spec, pageable), ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID userId, UUID expenseId) {
        return ExpenseResponse.from(loadOwned(userId, expenseId));
    }

    @Transactional
    public ExpenseResponse create(UUID userId, ExpenseRequest request) {
        Expense expense = new Expense(userRepository.getReferenceById(userId));
        apply(userId, expense, request);
        return ExpenseResponse.from(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(UUID userId, UUID expenseId, ExpenseRequest request) {
        if (request.version() == null) {
            throw ApiException.badRequest("version is required when updating an expense");
        }
        Expense expense = loadOwned(userId, expenseId);
        if (!expense.getVersion().equals(request.version())) {
            throw ApiException.conflict("This expense was changed elsewhere. Reload it and try again.");
        }
        apply(userId, expense, request);
        // Flush so the response carries the incremented version.
        return ExpenseResponse.from(expenseRepository.saveAndFlush(expense));
    }

    @Transactional
    public void delete(UUID userId, UUID expenseId) {
        expenseRepository.delete(loadOwned(userId, expenseId));
    }

    /** Totals grouped by category, using the same filters as the list. */
    @Transactional(readOnly = true)
    public ExpenseSummary summarize(UUID userId, ExpenseFilter filter) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Expense> root = query.from(Expense.class);
        Join<Expense, Category> category = root.join("category", JoinType.LEFT);
        Expression<BigDecimal> sum = cb.sum(root.get("amount"));
        Expression<Long> count = cb.count(root);

        query.select(cb.tuple(category.get("id"), category.get("name"), category.get("color"), sum, count))
                .where(filter.toPredicate(userId, root, cb))
                .groupBy(category.get("id"), category.get("name"), category.get("color"))
                .orderBy(cb.desc(sum));

        List<CategoryTotal> byCategory = entityManager.createQuery(query).getResultList().stream()
                .map(t -> new CategoryTotal(
                        t.get(0, UUID.class), t.get(1, String.class), t.get(2, String.class),
                        t.get(3, BigDecimal.class), t.get(4, Long.class)))
                .toList();

        BigDecimal total = byCategory.stream().map(CategoryTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        long totalCount = byCategory.stream().mapToLong(CategoryTotal::count).sum();
        return new ExpenseSummary(total, totalCount, byCategory);
    }

    /** Totals for the {@code months} calendar months ending with {@code last}, oldest first. Empty months are zero. */
    @Transactional(readOnly = true)
    public List<MonthTotal> monthlyTotals(UUID userId, YearMonth last, int months) {
        YearMonth first = last.minusMonths(months - 1L);
        Map<YearMonth, MonthTotal> found = expenseRepository
                .monthlyTotals(userId, first.atDay(1), last.atEndOfMonth()).stream()
                .map(row -> new MonthTotal(
                        YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue()),
                        (BigDecimal) row[2],
                        ((Number) row[3]).longValue()))
                .collect(Collectors.toMap(MonthTotal::month, Function.identity()));
        return Stream.iterate(first, month -> month.plusMonths(1))
                .limit(months)
                .map(month -> found.getOrDefault(month, new MonthTotal(month, ZERO, 0)))
                .toList();
    }

    private void apply(UUID userId, Expense expense, ExpenseRequest request) {
        expense.setDescription(request.description().trim());
        expense.setAmount(request.amount());
        expense.setExpenseDate(request.expenseDate());
        expense.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        expense.setCategory(request.categoryId() == null ? null : categoryService.loadAssignable(userId, request.categoryId()));
    }

    /** Other users' expenses are reported as not found, so their existence isn't leaked. */
    private Expense loadOwned(UUID userId, UUID expenseId) {
        return expenseRepository.findByIdAndOwnerId(expenseId, userId)
                .orElseThrow(() -> ApiException.notFound("Expense not found"));
    }

    /** Parses "date,desc"-style sort parameters. Newest-created is the tie-breaker for stable paging. */
    private static Sort parseSort(String sort) {
        String[] parts = (sort == null || sort.isBlank() ? "date,desc" : sort).split(",");
        String property = SORTABLE.get(parts[0].trim());
        if (property == null) {
            throw ApiException.badRequest("Cannot sort by '" + parts[0] + "'. Allowed: " + SORTABLE.keySet());
        }
        Sort.Direction direction = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
