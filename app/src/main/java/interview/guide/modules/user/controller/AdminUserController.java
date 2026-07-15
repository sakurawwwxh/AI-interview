package interview.guide.modules.user.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.user.model.AuthDTO;
import interview.guide.modules.user.model.AuthDTO.UserInfo;
import interview.guide.modules.user.model.AuthDTO.UserPage;
import interview.guide.modules.user.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<UserPage> listUsers(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return Result.success(adminUserService.listUsers(keyword, page, size));
    }

    @GetMapping("/{id}")
    public Result<UserInfo> getUser(@PathVariable Long id) {
        return Result.success(adminUserService.getUser(id));
    }

    @PutMapping("/{id}/role")
    public Result<UserInfo> updateRole(
        @PathVariable Long id,
        @Valid @RequestBody AuthDTO.UpdateRoleRequest request
    ) {
        return Result.success(adminUserService.updateRole(id, request.role()));
    }

    @PutMapping("/{id}/quota")
    public Result<UserInfo> updateQuota(
        @PathVariable Long id,
        @Valid @RequestBody AuthDTO.UpdateQuotaRequest request
    ) {
        return Result.success(adminUserService.updateQuota(id, request.dailyTokenQuota()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return Result.success(null);
    }
}
