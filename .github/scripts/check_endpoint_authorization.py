#!/usr/bin/env python3
"""
Fails the build if a REST endpoint ships without an authorization annotation.

The platform is deny-by-default at the filter chain, but a controller method with
no @PreAuthorize is reachable by *any* authenticated user regardless of role — a
silent privilege escalation rather than an outage, which is exactly the kind of
regression that survives code review. This check makes it a build failure.

Public endpoints must be named in PUBLIC_ENDPOINTS below, so exempting one is a
deliberate, reviewable act.
"""

import re
import sys
from pathlib import Path

CONTROLLER_DIR = Path("backend/src/main/java/com/idp/web")

# Endpoints intentionally reachable without authorization, as "<file>:<method>".
# Must match SecurityConfig's permitAll matchers.
PUBLIC_ENDPOINTS = {
    "HealthController.java:getHealth",
}

MAPPING = re.compile(r"@(Get|Post|Put|Patch|Delete|Request)Mapping\b")
PREAUTHORIZE = re.compile(r"@(PreAuthorize|PostAuthorize|Secured)\b")
# A method declaration, e.g. "public ResponseEntity<Foo> bar(" — captures the name.
METHOD = re.compile(r"^\s*(?:public|protected|private)\s+[\w<>,\[\]\s\.\?]+\s+(\w+)\s*\(")


def check_controller(path: Path):
    """Returns a list of "file:method" for endpoints missing an authorization annotation."""
    unprotected = []
    annotations = []

    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()

        if stripped.startswith("@"):
            annotations.append(stripped)
            continue

        method_match = METHOD.match(line)
        if method_match:
            block = " ".join(annotations)
            # Class-level @RequestMapping carries no method, so only flag blocks
            # that declare an HTTP verb mapping.
            if MAPPING.search(block) and not PREAUTHORIZE.search(block):
                unprotected.append(f"{path.name}:{method_match.group(1)}")

        # Any non-annotation line ends the current annotation block.
        if stripped and not stripped.startswith("@"):
            annotations = []

    return unprotected


def main():
    if not CONTROLLER_DIR.is_dir():
        print(f"ERROR: controller directory not found: {CONTROLLER_DIR}")
        return 1

    controllers = sorted(CONTROLLER_DIR.glob("*.java"))
    if not controllers:
        print(f"ERROR: no controllers found in {CONTROLLER_DIR}")
        return 1

    violations = []
    total_endpoints = 0

    for controller in controllers:
        source = controller.read_text(encoding="utf-8")
        total_endpoints += len(
            [m for m in MAPPING.finditer(source) if "RequestMapping" not in m.group(0)]
        )
        for endpoint in check_controller(controller):
            if endpoint not in PUBLIC_ENDPOINTS:
                violations.append(endpoint)

    print(f"Scanned {len(controllers)} controllers, {total_endpoints} endpoint mappings.")
    print(f"Exempted as public: {len(PUBLIC_ENDPOINTS)}")

    if violations:
        print("\nFAIL: these endpoints have no authorization annotation:\n")
        for violation in violations:
            print(f"  - {violation}")
        print(
            "\nAdd @PreAuthorize(\"hasPermission(...)\") so the RBAC/ABAC engine decides "
            "access.\nIf the endpoint is genuinely public, add it to PUBLIC_ENDPOINTS in "
            f"{__file__} and to SecurityConfig's permitAll matchers."
        )
        return 1

    print("\nOK: every endpoint is either authorized or explicitly exempted.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
