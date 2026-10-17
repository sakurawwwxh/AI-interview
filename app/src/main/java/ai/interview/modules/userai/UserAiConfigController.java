package ai.interview.modules.userai;
import ai.interview.common.annotation.RateLimit;
import ai.interview.common.result.Result;
import ai.interview.modules.userai.model.SaveUserAiConfigRequest;
import ai.interview.modules.userai.model.UserAiConfigDTO;
import ai.interview.modules.userai.service.UserAiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;
@RestController @RequestMapping("/api/ai-config") @RequiredArgsConstructor
public class UserAiConfigController {
 private final UserAiConfigService service;
 @GetMapping public Result<Optional<UserAiConfigDTO>> get(){return Result.success(service.get());}
 @PutMapping public Result<UserAiConfigDTO> save(@Valid @RequestBody SaveUserAiConfigRequest request){return Result.success(service.save(request));}
 @PostMapping("/test") @RateLimit(dimensions={RateLimit.Dimension.GLOBAL,RateLimit.Dimension.IP},count=3) public Result<Void> test(@Valid @RequestBody SaveUserAiConfigRequest request){service.test(request); return Result.success(null);}
 @DeleteMapping public Result<Void> delete(){service.delete();return Result.success(null);}
}
