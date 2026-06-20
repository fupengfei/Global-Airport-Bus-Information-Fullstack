import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('../api/favorites', () => ({
  listFavoriteIds: vi.fn(() => Promise.resolve(['vie-vab1'])),
  favorite: vi.fn(() => Promise.resolve({ favorited: true })),
  unfavorite: vi.fn(() => Promise.resolve({ favorited: false })),
}))

import * as api from '../api/favorites'
import { useFavorites } from '../stores/favorites'

describe('favorites store', () => {
  beforeEach(() => { setActivePinia(createPinia()); vi.clearAllMocks() })

  it('load fills the id set', async () => {
    const s = useFavorites()
    await s.load()
    expect(s.isFavorited('vie-vab1')).toBe(true)
  })

  it('toggle adds optimistically then calls favorite()', async () => {
    const s = useFavorites()
    let stateWhenCalled: boolean | undefined
    ;(api.favorite as any).mockImplementationOnce((sourceId: string) => {
      stateWhenCalled = s.isFavorited(sourceId) // 调用 favorite() 的瞬间,本地态必须已置位(乐观)
      return Promise.resolve({ favorited: true })
    })
    await s.toggle('pvg-line4')
    expect(stateWhenCalled).toBe(true)          // 证明先更新本地、再发请求
    expect(s.isFavorited('pvg-line4')).toBe(true)
    expect(api.favorite).toHaveBeenCalledWith('pvg-line4')
  })

  it('toggle removes an existing favorite via unfavorite()', async () => {
    const s = useFavorites()
    await s.load()
    await s.toggle('vie-vab1')
    expect(s.isFavorited('vie-vab1')).toBe(false)
    expect(api.unfavorite).toHaveBeenCalledWith('vie-vab1')
  })

  it('rolls back on failure', async () => {
    ;(api.favorite as any).mockRejectedValueOnce(new Error('boom'))
    const s = useFavorites()
    await expect(s.toggle('pvg-line7')).rejects.toThrow()
    expect(s.isFavorited('pvg-line7')).toBe(false) // 回滚
  })

  it('clear empties the set', async () => {
    const s = useFavorites()
    await s.load()
    s.clear()
    expect(s.isFavorited('vie-vab1')).toBe(false)
  })
})
