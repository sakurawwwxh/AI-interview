package interview.guide.modules.target.model;
import java.time.LocalDateTime;
import java.util.List;
public record JobMatchDTO(int score, String summary, List<String> matchedSkills,
                          List<String> missingSkills, List<String> suggestions, LocalDateTime analyzedAt) {}
