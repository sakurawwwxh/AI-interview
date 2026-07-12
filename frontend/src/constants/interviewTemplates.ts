import type { InterviewTemplateConfig } from '../types/interview';

export const interviewTemplates: InterviewTemplateConfig[] = [
  {
    id: 'backend-standard',
    name: '后端综合',
    questionTypes: [
      { type: 'PROJECT', weight: 20 }, { type: 'MYSQL', weight: 20 },
      { type: 'REDIS', weight: 20 }, { type: 'JAVA_BASIC', weight: 10 },
      { type: 'JAVA_COLLECTION', weight: 10 }, { type: 'JAVA_CONCURRENT', weight: 10 },
      { type: 'SPRING', weight: 10 },
    ],
    difficultyDistribution: { basic: 30, advanced: 50, expert: 20 },
    followUpCount: 1,
  },
  {
    id: 'frontend',
    name: '前端方向',
    questionTypes: [
      { type: 'PROJECT', weight: 25 }, { type: 'FRONTEND', weight: 35 },
      { type: 'ARCHITECTURE', weight: 15 }, { type: 'DISTRIBUTED_SYSTEM', weight: 10 },
      { type: 'SOFT_SKILLS', weight: 15 },
    ],
    difficultyDistribution: { basic: 35, advanced: 45, expert: 20 },
    followUpCount: 1,
  },
  {
    id: 'fullstack',
    name: '全栈进阶',
    questionTypes: [
      { type: 'PROJECT', weight: 25 }, { type: 'FRONTEND', weight: 20 },
      { type: 'SPRING', weight: 15 }, { type: 'MYSQL', weight: 10 },
      { type: 'REDIS', weight: 10 }, { type: 'ARCHITECTURE', weight: 10 },
      { type: 'SOFT_SKILLS', weight: 10 },
    ],
    difficultyDistribution: { basic: 20, advanced: 55, expert: 25 },
    followUpCount: 1,
  },
];
