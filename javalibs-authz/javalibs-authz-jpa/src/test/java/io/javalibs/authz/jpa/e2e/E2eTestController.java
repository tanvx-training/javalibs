package io.javalibs.authz.jpa.e2e;

import io.javalibs.authz.spring.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only endpoint guarded by a project-scoped permission. */
@RestController
public class E2eTestController {

    @GetMapping("/api/projects/{projectId}/issues")
    @RequirePermission(value = "issue.read", scopeType = "project", scopeIdParam = "projectId")
    public Map<String, String> issues(@PathVariable String projectId) {
        return Map.of("project", projectId);
    }
}
