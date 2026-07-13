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
  {
    id: 'hr-behavioral',
    name: 'HR 综合面',
    questionTypes: [
      { type: 'SOFT_SKILLS', weight: 45 }, { type: 'PROJECT', weight: 35 },
      { type: 'ARCHITECTURE', weight: 10 }, { type: 'FRONTEND', weight: 10 },
    ],
    difficultyDistribution: { basic: 45, advanced: 45, expert: 10 },
    followUpCount: 1,
  },
  {
    id: 'project-deep-dive',
    name: '项目深挖',
    questionTypes: [
      { type: 'PROJECT', weight: 65 }, { type: 'ARCHITECTURE', weight: 20 },
      { type: 'DISTRIBUTED_SYSTEM', weight: 15 },
    ],
    difficultyDistribution: { basic: 15, advanced: 55, expert: 30 },
    followUpCount: 2,
  },
  {
    id: 'pressure-challenge',
    name: '压力挑战',
    questionTypes: [
      { type: 'PROJECT', weight: 30 }, { type: 'JAVA_CONCURRENT', weight: 20 },
      { type: 'ARCHITECTURE', weight: 25 }, { type: 'DISTRIBUTED_SYSTEM', weight: 25 },
    ],
    difficultyDistribution: { basic: 10, advanced: 40, expert: 50 },
    followUpCount: 2,
  },
];
