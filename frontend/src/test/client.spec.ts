import { describe, it, expect } from 'vitest'
import { asApiError } from '../api/client'

describe('asApiError', () => {
  it('extracts the structured envelope from an axios error', () => {
    const fakeErr = {
      isAxiosError: true,
      response: { data: { code: 'BUS_NOT_FOUND', message: 'no bus', details: [], traceId: 't1' } },
    }
    const parsed = asApiError(fakeErr)
    expect(parsed?.code).toBe('BUS_NOT_FOUND')
  })

  it('returns null for non-envelope errors', () => {
    expect(asApiError(new Error('boom'))).toBeNull()
  })
})
