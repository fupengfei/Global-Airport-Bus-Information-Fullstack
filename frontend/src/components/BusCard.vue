<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter, useRoute } from 'vue-router'
import type { BusDetail } from '../api/bus'
import { useAuth } from '../stores/auth'
import { useFavorites } from '../stores/favorites'
import AlertList from './AlertList.vue'
import FreshnessBadge from './FreshnessBadge.vue'

const props = defineProps<{ bus: BusDetail; detailLink?: boolean }>()
const { t } = useI18n()

const auth = useAuth()
const favs = useFavorites()
const router = useRouter()
const route = useRoute()
const faved = computed(() => favs.isFavorited(props.bus.sourceId))
async function onFav() {
  if (!auth.isAuthed) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try { await favs.toggle(props.bus.sourceId) } catch { /* 401 由拦截器处理,其余忽略 */ }
}

// price 在设计稿里拆成「主价 / small 副价」,但 BusDetail.price 只是一段展示文本。
// 取第一行作为主价,其余作为 small,既忠实视觉又不伪造结构。
const priceMain = computed(() => (props.bus.price ?? '').split(/\n|·/)[0]?.trim() || props.bus.price || '')
const priceSub = computed(() => {
  const rest = (props.bus.price ?? '').split(/\n|·/).slice(1).join(' · ').trim()
  return rest || null
})
const hasSchedules = computed(() => props.bus.schedules.length > 0)
</script>

<template>
  <article class="card">
    <!-- 头部:route → dest → operator;右侧收藏心 + 价格 -->
    <div class="card__top">
      <div>
        <div class="route">{{ bus.route }}</div>
        <div class="dest">{{ bus.destination ?? bus.route }}</div>
        <div v-if="bus.operator" class="operator">{{ bus.operator }}</div>
      </div>
      <div class="topRight">
        <button
          class="favBtn"
          :class="{ favOn: faved }"
          :aria-pressed="faved"
          :title="auth.isAuthed ? (faved ? t('favorite.remove') : t('favorite.add')) : t('favorite.loginPrompt')"
          @click="onFav"
        >{{ faved ? '♥' : '♡' }}</button>
        <div v-if="priceMain" class="price">
          {{ priceMain }}<small v-if="priceSub">{{ priceSub }}</small>
        </div>
      </div>
    </div>

    <!-- 单方向:时长 / 运营 / 班次(本期 BusDetail 无双向字段,只渲染一栏) -->
    <div class="dirs">
      <div class="dir">
        <div class="dirHead">{{ t('detail.serviceHead') }} <small>{{ t('detail.serviceSub') }}</small></div>
        <div class="dirMeta">
          <div v-if="bus.duration"><span class="metaLabel">{{ t('detail.duration') }}</span><span class="metaVal">{{ bus.duration }}</span></div>
          <div v-if="bus.operatingHours"><span class="metaLabel">{{ t('detail.hours') }}</span><span class="metaVal">{{ bus.operatingHours }}</span></div>
        </div>
        <table v-if="hasSchedules" class="schedTable">
          <tbody>
            <tr v-for="(s, i) in bus.schedules" :key="i">
              <td class="schedTime">{{ s.timeRange ?? t('detail.schedAllDay') }}</td>
              <td class="schedInterval">{{ s.intervalText }}</td>
              <td class="schedNote">{{ s.note }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 途经站点:竖向时间轴,末站实心加粗 -->
    <div v-if="bus.stops.length" class="section">
      <div class="secLabel">{{ t('detail.stops') }}</div>
      <div class="stops">
        <template v-for="(stop, i) in bus.stops" :key="i">
          <div class="stopRow">
            <span class="stopDot" :class="{ stopDotEnd: i === bus.stops.length - 1 }"></span>
            <span :class="i === bus.stops.length - 1 ? 'stopNameEnd' : 'stopName'">{{ stop }}</span>
          </div>
          <div v-if="i < bus.stops.length - 1" class="stopLine"></div>
        </template>
      </div>
    </div>

    <!-- 提醒(过期过滤在 AlertList 内),放内容下方 -->
    <AlertList :alerts="bus.alerts" />

    <!-- 图片 / 文件 -->
    <div v-if="bus.images.length || bus.files.length" class="media">
      <img
        v-for="(img, i) in bus.images"
        :key="'img' + i"
        class="thumb"
        :src="img.url"
        :alt="img.caption ?? bus.route"
      />
      <div v-if="bus.files.length" class="files">
        <a
          v-for="(f, i) in bus.files"
          :key="'file' + i"
          class="fileLink"
          :href="f.url"
          target="_blank"
          rel="noopener"
        >📄 {{ f.name ?? f.url }}</a>
      </div>
    </div>

    <!-- 新鲜度 footer -->
    <div class="updated">
      <FreshnessBadge :last-updated="bus.lastUpdated" :fetch-failed="bus.fetchFailed" />
      <router-link
        v-if="detailLink"
        class="detailLink"
        :to="{ name: 'bus', params: { sourceId: bus.sourceId } }"
      >{{ t('detail.viewDetail') }} →</router-link>
      <span v-if="bus.officialUrl">
        <a class="detailLink" :href="bus.officialUrl" target="_blank" rel="noopener">{{ t('detail.official') }} ↗</a>
      </span>
    </div>
  </article>
</template>

<style scoped>
.topRight { display: flex; flex-direction: column; align-items: flex-end; gap: 6px; }
.favBtn {
  border: none; background: none; cursor: pointer; line-height: 1;
  font-size: 22px; color: var(--muted, #9aa0a6); padding: 2px 4px;
}
.favBtn.favOn { color: #e0245e; }
</style>
