import { matchPath } from 'react-router-dom';
import { intelligenceTitles } from '../features/capabilities/IntelligencePages';

const pageTitles: Record<string, string> = {
  '/workbench': '项目管理驾驶舱',
  '/workbench/revenue': '计收驾驶舱',
  '/workbench/scheduling': '排产驾驶舱',
  '/workbench/capability': '能力指标驾驶舱',
  '/projects': '项目列表',
  '/projects/new': '新建项目',
  '/projects/import': '项目导入',
  '/projects/:id': '项目详情',
  '/revenue': '计收列表',
  '/revenue/new': '新增计收',
  '/revenue/import': '计收导入',
  '/revenue/:id': '计收详情',
  '/scheduling': '排产计划',
  '/scheduling/new': '新增排产',
  '/scheduling/import': '排产导入',
  '/capability/gewu': '已部署格物项目清单',
  '/capability/gewu/import': '格物部署导入',
  '/approvals': '审批待办',
  '/approvals/:id': '申请详情',
  '/notifications': '消息通知',
  '/account': '个人设置',
  '/admin/accounts': '账号与组织',
  '/admin/backups': '备份管理',
  '/admin/inactivity': '督办管理',
  '/admin/reviewer-provinces': '接口人范围',
  '/admin/historical-snapshots': '历史快照导入',
  '/admin/historical-snapshots/import': '历史快照导入',
};

for (const [kind, title] of Object.entries(intelligenceTitles)) {
  pageTitles[`/capability/${kind}`] = title;
  pageTitles[`/capability/${kind}/import`] = `${title}导入`;
  pageTitles[`/capability/${kind}/:id`] = `${title}详情`;
}

export function pageTitle(pathname: string) {
  return (
    pageTitles[pathname] ??
    Object.entries(pageTitles).find(
      ([pattern]) => pattern.includes(':') && matchPath(pattern, pathname),
    )?.[1]
  );
}
