import { defineStore } from 'pinia'
import * as api from '../api/favorites'

export const useFavorites = defineStore('favorites', {
  state: () => ({ ids: new Set<string>() }),
  getters: {
    // 用函数 getter 按 sourceId 查询;Set 重新赋值触发响应式更新。
    isFavorited: (state) => (sourceId: string) => state.ids.has(sourceId),
  },
  actions: {
    async load() {
      this.ids = new Set(await api.listFavoriteIds())
    },
    clear() {
      this.ids = new Set()
    },
    async toggle(sourceId: string) {
      const had = this.ids.has(sourceId)
      const next = new Set(this.ids)
      if (had) next.delete(sourceId)
      else next.add(sourceId)
      this.ids = next // 乐观更新
      try {
        if (had) await api.unfavorite(sourceId)
        else await api.favorite(sourceId)
      } catch (e) {
        const rollback = new Set(this.ids)
        if (had) rollback.add(sourceId)
        else rollback.delete(sourceId)
        this.ids = rollback // 回滚
        throw e
      }
    },
  },
})
