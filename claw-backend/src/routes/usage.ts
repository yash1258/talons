import { Router } from 'express';
import { prisma } from '../db/prisma.js';
import { authenticate, AuthRequest } from '../middleware/auth.js';

export const usageRouter = Router();

usageRouter.use(authenticate);

usageRouter.get('/', async (req: AuthRequest, res) => {
  const { startDate, endDate } = req.query;
  
  const start = startDate ? new Date(startDate as string) : new Date(Date.now() - 30 * 24 * 60 * 60 * 1000);
  const end = endDate ? new Date(endDate as string) : new Date();

  const records = await prisma.usageRecord.findMany({
    where: {
      userId: req.userId,
      date: { gte: start, lte: end },
    },
    orderBy: { date: 'desc' },
  });

  const totals = records.reduce(
    (acc, r) => ({
      inputTokens: acc.inputTokens + r.inputTokens,
      outputTokens: acc.outputTokens + r.outputTokens,
      totalCost: acc.totalCost + r.totalCost,
      messagesCount: acc.messagesCount + r.messagesCount,
      toolCallsCount: acc.toolCallsCount + r.toolCallsCount,
    }),
    { inputTokens: 0, outputTokens: 0, totalCost: 0, messagesCount: 0, toolCallsCount: 0 }
  );

  res.json({ records, totals });
});

usageRouter.get('/subscription', async (req: AuthRequest, res) => {
  const user = await prisma.user.findUnique({
    where: { id: req.userId },
    include: { instances: true },
  });

  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }

  const limits = getSubscriptionLimits(user.subscription);
  const currentMonth = new Date();
  currentMonth.setDate(1);

  const monthUsage = await prisma.usageRecord.aggregate({
    where: {
      userId: req.userId,
      date: { gte: currentMonth },
    },
    _sum: {
      inputTokens: true,
      outputTokens: true,
      totalCost: true,
      messagesCount: true,
    },
  });

  res.json({
    subscription: user.subscription,
    limits,
    usage: {
      tokens: (monthUsage._sum.inputTokens || 0) + (monthUsage._sum.outputTokens || 0),
      messages: monthUsage._sum.messagesCount || 0,
      cost: monthUsage._sum.totalCost || 0,
    },
  });
});

function getSubscriptionLimits(subscription: string) {
  switch (subscription) {
    case 'PRO':
      return { tokens: 500000, messages: 5000, channels: 3 };
    case 'PREMIUM':
      return { tokens: -1, messages: -1, channels: -1 };
    default:
      return { tokens: 50000, messages: 500, channels: 1 };
  }
}
