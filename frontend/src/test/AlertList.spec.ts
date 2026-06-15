import { describe, it, expect } from 'vitest'
import { activeAlerts } from '../components/alertFilter'

const today = '2026-06-11'

describe('activeAlerts', () => {
  it('drops alerts whose endDate is before today', () => {
    const alerts = [
      { type: 'info', message: 'old', startDate: null, endDate: '2026-01-01' },
      { type: 'info', message: 'live', startDate: null, endDate: '2026-12-31' },
    ]
    expect(activeAlerts(alerts, today).map((a) => a.message)).toEqual(['live'])
  })

  it('keeps alerts with no endDate (long-term)', () => {
    const alerts = [{ type: 'warn', message: 'forever', startDate: null, endDate: null }]
    expect(activeAlerts(alerts, today)).toHaveLength(1)
  })
})
