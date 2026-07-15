import type { InterviewTemplateConfig, QuestionType } from '../types/interview';

/** JD 关键词 → 题型，用于岗位推荐模板 */
const TYPE_KEYWORDS: Partial<Record<QuestionType, string[]>> = {
  MYSQL: ['mysql', 'sql', '数据库', '索引', '事务', 'innodb', '分库'],
  REDIS: ['redis', '缓存', '分布式锁', '缓存一致性'],
  JAVA_BASIC: ['java', 'jvm', 'gc', '垃圾回收', '面向对象'],
  JAVA_COLLECTION: ['集合', 'hashmap', 'concurrenthashmap', 'list', 'map'],
  JAVA_CONCURRENT: ['并发', '多线程', '线程池', '锁', 'synchronized', 'juc'],
  SPRING: ['spring', 'ioc', 'aop', 'bean', '依赖注入'],
  SPRING_BOOT: ['spring boot', 'springboot', 'starter', '自动配置'],
  FRONTEND: ['前端', 'react', 'vue', 'javascript', 'typescript', 'css', 'html', 'webpack'],
  DISTRIBUTED_SYSTEM: ['分布式', '微服务', 'rpc', '消息队列', 'kafka', 'mq', 'nacos'],
  ARCHITECTURE: ['架构', '高可用', '高并发', '系统设计', '限流', '降级'],
  PROJECT: ['项目', '业务', '落地', '负责'],
  SOFT_SKILLS: ['沟通', '协作', '领导力', '软技能', '团队', 'hr', '行为'],
};

export interface TemplateRecommendation {
  template: InterviewTemplateConfig;
  score: number;
  /** 命中的题型说明，便于 UI 展示 */
  matchedTypes: string[];
  reason: string;
}

/**
 * 根据 JD 文本为模板打分：模板内题型权重 × JD 命中次数。
 */
export function scoreTemplateAgainstJd(
  template: InterviewTemplateConfig,
  jobDescription: string,
): TemplateRecommendation {
  const jd = (jobDescription || '').toLowerCase();
  let score = 0;
  const matchedTypes: string[] = [];

  for (const { type, weight } of template.questionTypes) {
    const keywords = TYPE_KEYWORDS[type] ?? [];
    const hits = keywords.filter((k) => jd.includes(k.toLowerCase())).length;
    if (hits > 0) {
      score += weight * (1 + hits * 0.35);
      matchedTypes.push(type);
    }
  }

  // 轻微倾向：前端/后端/HR 关键字对对应模板给基础分
  const nameBoost = nameHintBoost(template.id, jd);
  score += nameBoost;

  const reason =
    matchedTypes.length > 0
      ? `JD 命中 ${matchedTypes.slice(0, 4).join('、')}${matchedTypes.length > 4 ? ' 等' : ''} 相关能力`
      : '与 JD 关键词匹配较弱，可作为综合备选';

  return { template, score, matchedTypes, reason };
}

function nameHintBoost(templateId: string, jd: string): number {
  if (/(前端|react|vue|typescript|javascript)/.test(jd) && templateId === 'frontend') return 25;
  if (/(全栈|fullstack|前后端)/.test(jd) && templateId === 'fullstack') return 25;
  if (/(hr|行为面|软技能|沟通协作)/.test(jd) && templateId === 'hr-behavioral') return 25;
  if (/(架构|高可用|高并发|分布式)/.test(jd) && templateId === 'pressure-challenge') return 15;
  if (/(项目|负责|业务)/.test(jd) && templateId === 'project-deep-dive') return 12;
  if (/(后端|java|spring|mysql|redis)/.test(jd) && templateId === 'backend-standard') return 18;
  return 0;
}

/**
 * 从模板列表中推荐与 JD 最匹配的一项。
 */
export function recommendTemplate(
  jobDescription: string,
  templates: InterviewTemplateConfig[],
): TemplateRecommendation | null {
  if (!jobDescription?.trim() || !templates.length) {
    return null;
  }
  let best: TemplateRecommendation | null = null;
  for (const template of templates) {
    const scored = scoreTemplateAgainstJd(template, jobDescription);
    if (!best || scored.score > best.score) {
      best = scored;
    }
  }
  // 无任何命中时仍返回后端综合，避免空推荐
  if (best && best.score <= 0) {
    const fallback = templates.find((t) => t.id === 'backend-standard') ?? templates[0];
    return {
      template: fallback,
      score: 0,
      matchedTypes: [],
      reason: '未识别到明确技术栈，已推荐通用后端综合模板',
    };
  }
  return best;
}
