"""
Keeps trying to create the Always Free Ampere VM until Oracle has capacity for it.

Free A1 instances are often "Out of host capacity" in every availability domain. Capacity frees up
over time, so this asks again every few minutes (all availability domains in turn) and stops as soon
as one launch succeeds. It reuses an existing VCN/subnet, so it never creates extra networks.

Uses the OCI Python SDK (installed with `pip install oci-cli`) and the CLI's config file
(~/.oci/config). See deploy/DEPLOY.md for setting that up.

    python deploy/oci_launch_retry.py --check   # resolve everything and print it, launch nothing
    python deploy/oci_launch_retry.py           # retry until the instance exists (Ctrl+C to stop)
"""

import argparse
import sys
import time
from datetime import datetime
from pathlib import Path

try:
    import oci
except ImportError:
    sys.exit("The OCI SDK isn't installed. Run: python -m pip install --user oci-cli")

SHAPE = "VM.Standard.A1.Flex"
LIVE_STATES = {"PROVISIONING", "STARTING", "RUNNING", "STOPPING", "STOPPED", "CREATING_IMAGE", "MOVING"}


def log(message: str) -> None:
    print(f"[{datetime.now():%Y-%m-%d %H:%M:%S}] {message}", flush=True)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Retry launching the Always Free A1 instance until there's capacity.")
    parser.add_argument("--check", action="store_true", help="resolve settings and print them, but don't launch")
    parser.add_argument("--name", default="splitledger", help="instance display name (default: splitledger)")
    parser.add_argument("--ocpus", type=float, default=1, help="OCPUs (default: 1; the free pool is 2 in total)")
    parser.add_argument("--memory", type=float, default=6, help="memory in GB (default: 6; the free pool is 12 in total)")
    parser.add_argument("--subnet-name", default="splitledger-subnet", help="existing public subnet to use")
    parser.add_argument("--subnet-id", help="subnet OCID, if several subnets share the name")
    parser.add_argument("--image-id", help="image OCID (default: newest Canonical Ubuntu 24.04 for the A1 shape)")
    parser.add_argument("--ssh-key", default=str(Path.home() / ".ssh" / "id_ed25519.pub"), help="public key file")
    parser.add_argument("--interval", type=int, default=120, help="seconds between rounds (default: 120)")
    parser.add_argument("--profile", default="DEFAULT", help="profile in ~/.oci/config (default: DEFAULT)")
    return parser.parse_args()


class Settings:
    """Everything the launch needs, looked up once at startup."""

    def __init__(self, args: argparse.Namespace):
        self.args = args
        try:
            self.config = oci.config.from_file(profile_name=args.profile)
            oci.config.validate_config(self.config)
        except Exception as e:  # missing file, bad key path, ...
            sys.exit(f"Couldn't load ~/.oci/config (profile {args.profile}): {e}")

        # Always Free resources live in the root compartment, whose OCID is the tenancy OCID.
        self.compartment_id = self.config["tenancy"]
        self.identity = oci.identity.IdentityClient(self.config)
        self.compute = oci.core.ComputeClient(self.config)
        self.network = oci.core.VirtualNetworkClient(self.config)

        self.availability_domains = [ad.name for ad in self.identity.list_availability_domains(self.compartment_id).data]
        self.image = self._find_image()
        self.subnet = self._find_subnet()
        self.ssh_key = self._read_ssh_key()

    def _find_image(self):
        if self.args.image_id:
            return self.compute.get_image(self.args.image_id).data
        images = oci.pagination.list_call_get_all_results(
            self.compute.list_images,
            self.compartment_id,
            operating_system="Canonical Ubuntu",
            operating_system_version="24.04",
            shape=SHAPE,
            sort_by="TIMECREATED",
            sort_order="DESC",
        ).data
        full = [i for i in images if "minimal" not in i.display_name.lower()]
        if not full:
            sys.exit("No Canonical Ubuntu 24.04 image found for the A1 shape. Pass --image-id.")
        return full[0]

    def _available_subnets(self) -> str:
        """Every usable subnet in the root compartment, one per line, for error messages."""
        subnets = [
            s for s in oci.pagination.list_call_get_all_results(self.network.list_subnets, self.compartment_id).data
            if s.lifecycle_state == "AVAILABLE"
        ]
        if not subnets:
            return "There are no subnets at all. Create the VCN first (see deploy/DEPLOY.md, step 1)."
        lines = [
            f"  {s.id}\n      name: {s.display_name}, VCN: {self.network.get_vcn(s.vcn_id).data.display_name}, "
            f"{'private' if s.prohibit_public_ip_on_vnic else 'public'}"
            for s in subnets
        ]
        return "Subnets that exist (use a public one with --subnet-id):\n" + "\n".join(lines)

    def _find_subnet(self):
        if self.args.subnet_id:
            try:
                subnet = self.network.get_subnet(self.args.subnet_id).data
            except oci.exceptions.ServiceError as e:
                if e.status != 404:
                    raise
                sys.exit(f"Subnet {self.args.subnet_id} doesn't exist (deleted, or the ID was copied incompletely).\n"
                         + self._available_subnets())
        else:
            subnets = [
                s for s in oci.pagination.list_call_get_all_results(
                    self.network.list_subnets, self.compartment_id, display_name=self.args.subnet_name
                ).data
                if s.lifecycle_state == "AVAILABLE"
            ]
            if not subnets:
                sys.exit(f"No subnet named '{self.args.subnet_name}'.\n" + self._available_subnets())
            if len(subnets) > 1:
                lines = [f"  {s.id}  (VCN: {self.network.get_vcn(s.vcn_id).data.display_name})" for s in subnets]
                sys.exit(f"Several subnets are named '{self.args.subnet_name}'. Delete the duplicate VCNs, "
                         "or pick one with --subnet-id:\n" + "\n".join(lines))
            subnet = subnets[0]
        if subnet.prohibit_public_ip_on_vnic:
            sys.exit(f"Subnet '{subnet.display_name}' is private, so the VM couldn't get a public IP. Use a public subnet.")
        return subnet

    def _read_ssh_key(self) -> str:
        path = Path(self.args.ssh_key)
        if not path.is_file():
            sys.exit(f"SSH public key not found: {path}. Create one with: ssh-keygen -t ed25519")
        key = path.read_text().strip()
        if path.suffix != ".pub" or not key.startswith("ssh-"):
            sys.exit(f"{path} doesn't look like a public key (expected an .pub file starting with 'ssh-').")
        return key

    def describe(self) -> None:
        vcn = self.network.get_vcn(self.subnet.vcn_id).data
        log(f"Region:               {self.config['region']}")
        log(f"Availability domains: {', '.join(self.availability_domains)}")
        log(f"Shape:                {SHAPE}, {self.args.ocpus:g} OCPU, {self.args.memory:g} GB")
        log(f"Image:                {self.image.display_name}")
        log(f"Subnet:               {self.subnet.display_name} (VCN: {vcn.display_name}), public IP: yes")
        log(f"SSH key:              {self.args.ssh_key}")


def existing_instance(s: Settings):
    """An instance with our name that isn't terminated, if one exists (so we never create two)."""
    instances = oci.pagination.list_call_get_all_results(
        s.compute.list_instances, s.compartment_id, display_name=s.args.name
    ).data
    return next((i for i in instances if i.lifecycle_state in LIVE_STATES), None)


def launch(s: Settings, availability_domain: str):
    details = oci.core.models.LaunchInstanceDetails(
        compartment_id=s.compartment_id,
        availability_domain=availability_domain,
        display_name=s.args.name,
        shape=SHAPE,
        shape_config=oci.core.models.LaunchInstanceShapeConfigDetails(ocpus=s.args.ocpus, memory_in_gbs=s.args.memory),
        source_details=oci.core.models.InstanceSourceViaImageDetails(image_id=s.image.id),
        create_vnic_details=oci.core.models.CreateVnicDetails(subnet_id=s.subnet.id, assign_public_ip=True),
        metadata={"ssh_authorized_keys": s.ssh_key},
    )
    # No SDK-level retries: this loop decides when to try again.
    return s.compute.launch_instance(details, retry_strategy=oci.retry.NoneRetryStrategy()).data


def report_success(s: Settings, instance) -> None:
    log(f"Launched {instance.display_name} in {instance.availability_domain}. Waiting for it to be RUNNING...")
    instance = oci.wait_until(
        s.compute, s.compute.get_instance(instance.id), "lifecycle_state", "RUNNING", max_wait_seconds=900
    ).data
    public_ip = None
    for _ in range(30):  # the VNIC can take a moment to attach
        attachments = s.compute.list_vnic_attachments(s.compartment_id, instance_id=instance.id).data
        attached = [a for a in attachments if a.lifecycle_state == "ATTACHED"]
        if attached:
            public_ip = s.network.get_vnic(attached[0].vnic_id).data.public_ip
            break
        time.sleep(10)

    print("\a", end="")
    log("=" * 60)
    log(f"SUCCESS: {instance.display_name} is RUNNING")
    log(f"Public IP: {public_ip or '(none yet: see the instance page in the console)'}")
    if public_ip:
        log(f"Connect:   ssh ubuntu@{public_ip}")
    log("=" * 60)
    if sys.platform == "win32":  # the script may run for days, so make the success hard to miss
        try:
            import ctypes
            ctypes.windll.user32.MessageBoxW(0, f"The OCI instance is running.\nPublic IP: {public_ip}", "SplitLedger VM ready", 0x40)
        except Exception:
            pass


def main() -> None:
    args = parse_args()
    try:
        s = Settings(args)
    except oci.exceptions.ServiceError as e:
        sys.exit(f"OCI refused a lookup during setup: {e.status} {e.code}: {e.message}")
    s.describe()

    found = existing_instance(s)
    if found:
        log(f"An instance named '{args.name}' already exists ({found.lifecycle_state}). Nothing to do.")
        return
    if args.check:
        log("Check passed: everything needed for the launch was found. Run again without --check to start retrying.")
        return

    log(f"Trying every {args.interval}s across {len(s.availability_domains)} availability domain(s). Ctrl+C to stop.")
    attempt = 0
    backoff = args.interval
    while True:
        for ad in s.availability_domains:
            attempt += 1
            try:
                report_success(s, launch(s, ad))
                return
            except oci.exceptions.ServiceError as e:
                message = (e.message or "").lower()
                if e.status == 500 and "capacity" in message:
                    log(f"#{attempt} {ad}: out of capacity")
                elif e.status == 429:
                    backoff = min(backoff * 2, 900)
                    log(f"#{attempt} {ad}: rate-limited by OCI, slowing down to every {backoff}s")
                    break
                elif e.code == "LimitExceeded":
                    sys.exit(f"Service limit exceeded: {e.message}\nCheck Compute → Instances for other A1 instances "
                             f"using the free pool (2 OCPU / 12 GB in total).")
                else:
                    sys.exit(f"Launch failed with an unexpected error, so stopping: {e.status} {e.code}: {e.message}")
            except (oci.exceptions.RequestException, oci.exceptions.ConnectTimeout) as e:
                # Network trouble: a launch may or may not have gone through, so check before going on.
                log(f"#{attempt} {ad}: network error ({type(e).__name__}), will check and retry")
                time.sleep(15)
                if existing_instance(s):
                    log("An instance was created despite the error.")
                    report_success(s, existing_instance(s))
                    return
            time.sleep(5)  # small gap between availability domains
        else:
            backoff = max(args.interval, int(backoff * 0.75))  # recover gradually after rate limiting
        time.sleep(backoff)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        log("Stopped.")
