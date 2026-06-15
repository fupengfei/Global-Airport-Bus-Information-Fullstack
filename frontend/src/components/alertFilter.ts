import type { Alert } from '../api/bus'

/** end_date 早于今天的过期 alert 过滤掉;无 end_date 视为长期保留(DS3)。 */
export function activeAlerts(alerts: Alert[], today: string): Alert[] {
  return alerts.filter((a) => !a.endDate || a.endDate >= today)
}
