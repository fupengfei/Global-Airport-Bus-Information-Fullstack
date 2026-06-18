# 查询主线 Bug 修复(5 项)Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复查询主线 5 个问题:#1 站点首节点=机场、#4 选中机场后全部线路完整列出、#5 详情页入口、#6 按站点搜索、#8 德语。

**Architecture:** #1/#6 在后端(纯函数排序 + 新搜索端点);#4/#5/#8 在前端。后端 Spring Boot + MyBatis(只用 `#{}`);前端 Vue 3 + vue-query + vue-i18n。

**Tech Stack:** Java 21 / Spring Boot / MyBatis / JUnit5 / Testcontainers;Vue 3 / TypeScript / Vitest。

**测试命令:** 后端 `cd backend && mvn -q test`(IT 需 Docker);前端 `cd frontend && npm test`。

---

### Task 1: #1 机场端排序纯函数 + 单测(全 11 条种子)

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/service/AirportStopOrderer.java`
- Test: `backend/src/test/java/com/airportbus/bus/service/AirportStopOrdererTest.java`

**算法:** 机场必是线路端点。取 `airportName` 去掉 `cityName` 与通用词后得到「特征 ref」(如 上海虹桥国际机场→虹桥;维也纳国际机场→空)。两端点谁 `contains(ref)` 谁是机场端;ref 为空/打平时,退化为「机场关键词(机场/航站楼/airport/terminal)」判断;仍打平则保持原序。机场端在末尾则反转,保证 `stops[0]` 是机场端。

- [ ] **Step 1: 写失败单测**

```java
package com.airportbus.bus.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class AirportStopOrdererTest {

    private String first(List<String> stops, String airport, String city) {
        return AirportStopOrderer.airportFirst(stops, airport, city).get(0);
    }

    @Test
    void airportEndpointAlwaysFirst() {
        // 机场在末尾 → 反转
        assertThat(first(List.of("维也纳西站 Westbahnhof", "维也纳中央车站 Hauptbahnhof (南入口)", "维也纳机场"),
                "维也纳国际机场", "维也纳")).isEqualTo("维也纳机场");
        assertThat(first(List.of("Morzinplatz / Schwedenplatz", "维也纳机场"),
                "维也纳国际机场", "维也纳")).isEqualTo("维也纳机场");
        // 机场在开头 → 保持
        assertThat(first(List.of("浦东机场", "上海火车站(北广场)"),
                "上海浦东国际机场", "上海")).isEqualTo("浦东机场");
        assertThat(first(List.of("浦东机场", "上海南站"),
                "上海浦东国际机场", "上海")).isEqualTo("浦东机场");
        // 机场端不含「机场」字样(虹桥东交通中心),靠特征 ref「虹桥」判定
        assertThat(first(List.of("虹桥东交通中心", "上海火车站(南广场)"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥东交通中心");
        // 另一端含「机场」但属别的机场(浦东),不能误选 → 仍选虹桥端
        assertThat(first(List.of("虹桥东交通中心", "浦东国际机场"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥东交通中心");
        assertThat(first(List.of("虹桥机场 T1 航站楼", "清涧新村"),
                "上海虹桥国际机场", "上海")).isEqualTo("虹桥机场 T1 航站楼");
        // 机场内摆渡(停车库 ↔ 航站楼),ref 打平 → 关键词「航站楼」兜底
        assertThat(first(List.of("P4 长时停车库", "T1 航站楼", "T2 航站楼"),
                "上海浦东国际机场", "上海")).isEqualTo("T2 航站楼");
    }

    @Test
    void shortListsUntouched() {
        assertThat(AirportStopOrderer.airportFirst(List.of("唯一站"), "X机场", "X")).containsExactly("唯一站");
        assertThat(AirportStopOrderer.airportFirst(List.of(), "X机场", "X")).isEmpty();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && mvn -q -Dtest=AirportStopOrdererTest test`
Expected: 编译失败 / `AirportStopOrderer` 不存在。

- [ ] **Step 3: 实现**

```java
package com.airportbus.bus.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/** 让 stops 的首元素始终是机场端(展示期重排,不改库内 seq)。 */
public final class AirportStopOrderer {

    private AirportStopOrderer() {}

    private static final String[] GENERIC = {
            "国际", "航站楼", "航站", "机场", "枢纽", "交通中心", "交通", "中心",
            "火车站", "车站", "地铁", "站", "international", "airport", "terminal",
            "station", "bus", "center", "centre"
    };
    private static final String[] AIRPORT_WORDS = {"机场", "航站楼", "航站", "airport", "terminal"};

    public static List<String> airportFirst(List<String> stops, String airportName, String cityName) {
        if (stops == null || stops.size() < 2) return stops;
        String ref = distinctiveRef(airportName, cityName);
        String first = stops.get(0), last = stops.get(stops.size() - 1);
        boolean reverse;
        int sf = score(first, ref), sl = score(last, ref);
        if (sf != sl) {
            reverse = sl > sf;
        } else {
            int af = airportness(first), al = airportness(last);
            reverse = al > af; // 打平(al==af)时 false,保持原序
        }
        if (!reverse) return stops;
        List<String> copy = new ArrayList<>(stops);
        java.util.Collections.reverse(copy);
        return copy;
    }

    private static int score(String stop, String ref) {
        if (ref.isEmpty()) return 0;
        return norm(stop).contains(ref) ? ref.length() : 0;
    }

    private static int airportness(String stop) {
        String n = norm(stop);
        int c = 0;
        for (String w : AIRPORT_WORDS) if (n.contains(w)) c++;
        return c;
    }

    /** airportName 去掉 cityName 与通用词,得到机场特征(如 虹桥 / 浦东;维也纳国际机场→空)。 */
    private static String distinctiveRef(String airportName, String cityName) {
        String r = norm(airportName);
        if (cityName != null) r = r.replace(norm(cityName), "");
        for (String g : GENERIC) r = r.replace(g, "");
        return r;
    }

    /** NFC + 小写 + 去空白与标点,保留 CJK / 字母 / 数字。 */
    private static String norm(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFC).toLowerCase();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && mvn -q -Dtest=AirportStopOrdererTest test`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/service/AirportStopOrderer.java backend/src/test/java/com/airportbus/bus/service/AirportStopOrdererTest.java
git commit -m "feat(bus): 机场端排序纯函数,保证途经站点首节点为机场(#1)"
```

---

### Task 2: #1 接入 detail(mapper 取机场/城市名 + 改 service + 更新 IT)

**Files:**
- Modify: `backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java`
- Modify: `backend/src/main/resources/mapper/BusQueryMapper.xml`
- Modify: `backend/src/main/java/com/airportbus/bus/service/BusQueryService.java:63-70`
- Modify: `backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java:56-57`

- [ ] **Step 1: 更新 IT 断言为机场在首(失败)**

将 `detailLoadsStopsInOrder` 的断言改为:

```java
        assertThat(d.stops()).containsExactly(
                "维也纳机场", "维也纳中央车站 Hauptbahnhof (南入口)", "维也纳西站 Westbahnhof");
```

- [ ] **Step 2: mapper 增加查询机场/城市名**

`BusQueryMapper.java` 接口里新增方法 + record:

```java
    RouteAirport selectRouteAirportCity(@Param("sourceId") String sourceId);

    record RouteAirport(String airportName, String cityName) {}
```

`BusQueryMapper.xml` 末尾 `</mapper>` 前新增:

```xml
  <select id="selectRouteAirportCity" resultType="com.airportbus.bus.mapper.BusQueryMapper$RouteAirport">
    SELECT a.name AS airportName, ci.name AS cityName
    FROM bus_route b
    JOIN airport a ON a.id = b.airport_id
    JOIN city ci   ON ci.id = a.city_id
    WHERE b.source_id = #{sourceId} AND b.deleted = 0
  </select>
```

- [ ] **Step 3: service.detail 调用排序器**

`BusQueryService.detail` 里把 `mapper.selectStops(h.id())` 改为排序后的列表:

```java
        BusQueryMapper.RouteAirport ra = mapper.selectRouteAirportCity(sourceId);
        List<String> stops = AirportStopOrderer.airportFirst(
                mapper.selectStops(h.id()),
                ra == null ? null : ra.airportName(),
                ra == null ? null : ra.cityName());
        return new BusDetailDto(
                h.sourceId(), h.route(), h.destination(), h.operator(), h.officialUrl(),
                h.duration(), h.price(), h.operatingHours(), h.lastUpdated(), h.fetchFailed(),
                stops,
                mapper.selectSchedules(h.id()),
                mapper.selectImages(h.id()),
                mapper.selectFiles(h.id()),
                mapper.selectAlerts(h.id()));
```

- [ ] **Step 4: 运行 IT(需 Docker)**

Run: `cd backend && mvn -q -Dtest=BusQueryServiceIT test`
Expected: PASS(`detailLoadsStopsInOrder` 现期望机场在首)。

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java backend/src/main/resources/mapper/BusQueryMapper.xml backend/src/main/java/com/airportbus/bus/service/BusQueryService.java backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java
git commit -m "feat(bus): detail 读取期把机场端排到途经站点首位(#1)"
```

---

### Task 3: #6 按站点搜索 — 后端端点

**Files:**
- Create: `backend/src/main/java/com/airportbus/bus/api/dto/SearchResultDto.java`
- Modify: `backend/src/main/java/com/airportbus/bus/mapper/BusQueryMapper.java`
- Modify: `backend/src/main/resources/mapper/BusQueryMapper.xml`
- Modify: `backend/src/main/java/com/airportbus/bus/service/BusQueryService.java`
- Modify: `backend/src/main/java/com/airportbus/bus/api/BusQueryController.java`
- Test: `backend/src/test/java/com/airportbus/bus/api/BusQueryControllerTest.java`(新增用例)
- Test: `backend/src/test/java/com/airportbus/bus/service/BusQueryServiceIT.java`(新增用例)

- [ ] **Step 1: 控制器层新增失败用例**

`BusQueryControllerTest` 顶部补 import:

```java
import com.airportbus.bus.api.dto.SearchResultDto;
```

新增方法:

```java
    @Test
    void searchReturnsAirportsAndRoutes() throws Exception {
        when(service.search("中央")).thenReturn(new SearchResultDto(
                List.of(new SearchResultDto.AirportHit("VIE", "维也纳国际机场", "Vienna", "AT")),
                List.of(new SearchResultDto.RouteHit("vie-vab1", "VAB 1", "西站", "VIE", "维也纳中央车站 Hauptbahnhof (南入口)"))));
        mvc.perform(get("/api/v1/search").param("q", "中央"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.airports[0].code").value("VIE"))
           .andExpect(jsonPath("$.routes[0].sourceId").value("vie-vab1"))
           .andExpect(jsonPath("$.routes[0].matchedStop").value("维也纳中央车站 Hauptbahnhof (南入口)"));
    }

    @Test
    void blankSearchReturnsEmpty() throws Exception {
        when(service.search("")).thenReturn(new SearchResultDto(List.of(), List.of()));
        mvc.perform(get("/api/v1/search").param("q", ""))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.airports").isEmpty())
           .andExpect(jsonPath("$.routes").isEmpty());
    }
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && mvn -q -Dtest=BusQueryControllerTest test`
Expected: 编译失败(`SearchResultDto` / `service.search` 不存在)。

- [ ] **Step 3: 新建 DTO**

```java
package com.airportbus.bus.api.dto;

import java.util.List;

public record SearchResultDto(List<AirportHit> airports, List<RouteHit> routes) {
    public record AirportHit(String code, String name, String cityName, String countryCode) {}
    public record RouteHit(String sourceId, String route, String destination, String airportCode, String matchedStop) {}
}
```

- [ ] **Step 4: mapper 接口 + XML**

`BusQueryMapper.java` 新增:

```java
    List<com.airportbus.bus.api.dto.SearchResultDto.AirportHit> searchAirports(@Param("q") String q);
    List<com.airportbus.bus.api.dto.SearchResultDto.RouteHit> searchRoutesByStop(@Param("q") String q);
```

`BusQueryMapper.xml` 末尾 `</mapper>` 前新增:

```xml
  <select id="searchAirports" resultType="com.airportbus.bus.api.dto.SearchResultDto$AirportHit">
    SELECT a.code, a.name, ci.name AS cityName, co.code AS countryCode
    FROM airport a
    JOIN city ci    ON ci.id = a.city_id
    JOIN country co ON co.id = ci.country_id
    WHERE a.deleted = 0
      AND (a.name LIKE CONCAT('%', #{q}, '%')
        OR a.code LIKE CONCAT('%', #{q}, '%')
        OR ci.name LIKE CONCAT('%', #{q}, '%'))
    ORDER BY a.name
    LIMIT 10
  </select>

  <select id="searchRoutesByStop" resultType="com.airportbus.bus.api.dto.SearchResultDto$RouteHit">
    SELECT b.source_id AS sourceId, b.route, b.destination, a.code AS airportCode,
           MIN(s.name) AS matchedStop
    FROM bus_stop s
    JOIN bus_route b ON b.id = s.bus_route_id
    JOIN airport a   ON a.id = b.airport_id
    WHERE s.deleted = 0 AND b.deleted = 0
      AND s.name LIKE CONCAT('%', #{q}, '%')
    GROUP BY b.source_id, b.route, b.destination, a.code
    ORDER BY b.source_id
    LIMIT 10
  </select>
```

- [ ] **Step 5: service.search**

`BusQueryService` 增加(import `SearchResultDto`):

```java
    public SearchResultDto search(String q) {
        String t = q == null ? "" : q.trim();
        if (t.isEmpty()) return new SearchResultDto(List.of(), List.of());
        return new SearchResultDto(mapper.searchAirports(t), mapper.searchRoutesByStop(t));
    }
```

- [ ] **Step 6: controller 端点**

`BusQueryController` 增加:

```java
    @Operation(summary = "搜索机场与按站点匹配的线路")
    @GetMapping("/search")
    public com.airportbus.bus.api.dto.SearchResultDto search(@RequestParam("q") String q) {
        return service.search(q);
    }
```

- [ ] **Step 7: IT 新增站点搜索用例**

`BusQueryServiceIT` 增加(import `SearchResultDto`):

```java
    @Test
    void searchByStopNameMatchesRoute() {
        var r = service.search("中央车站");
        assertThat(r.routes()).extracting(SearchResultDto.RouteHit::sourceId).contains("vie-vab1");
    }

    @Test
    void blankSearchReturnsEmpty() {
        var r = service.search("   ");
        assertThat(r.airports()).isEmpty();
        assertThat(r.routes()).isEmpty();
    }
```

- [ ] **Step 8: 运行**

Run: `cd backend && mvn -q -Dtest=BusQueryControllerTest,BusQueryServiceIT test`
Expected: PASS。

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/airportbus backend/src/main/resources/mapper/BusQueryMapper.xml backend/src/test/java/com/airportbus
git commit -m "feat(bus): 新增 /api/v1/search 按站点名搜索线路 + 机场(#6)"
```

---

### Task 4: #6 前端接入搜索端点

**Files:**
- Modify: `frontend/src/api/bus.ts`
- Modify: `frontend/src/pages/HomePage.vue`

- [ ] **Step 1: api 增加 search**

`frontend/src/api/bus.ts` 末尾追加:

```ts
export interface SearchResult {
  airports: { code: string; name: string; cityName: string; countryCode: string }[]
  routes: { sourceId: string; route: string; destination: string | null; airportCode: string; matchedStop: string }[]
}
export const search = (q: string) =>
  http.get<SearchResult>('/search', { params: { q } }).then((r) => r.data)
```

- [ ] **Step 2: HomePage 用服务端搜索替换纯客户端过滤**

`HomePage.vue` `<script setup>` 内:删除原 `allAirports` / `searchHits`(L31-45)的客户端逻辑,改为:

```ts
import { useRouter } from 'vue-router'
import { getAirportBuses, getBusDetail, getTree, search as searchApi } from '../api/bus'

const router = useRouter()

// 防抖后的查询词
const debounced = ref('')
let timer: ReturnType<typeof setTimeout> | undefined
watch(search, (v) => {
  clearTimeout(timer)
  timer = setTimeout(() => { debounced.value = v.trim() }, 250)
})

const searchQ = useQuery({
  queryKey: ['search', debounced],
  queryFn: () => searchApi(debounced.value),
  enabled: computed(() => debounced.value.length >= 1),
})
const airportHits = computed(() => searchQ.data.value?.airports ?? [])
const routeHits = computed(() => searchQ.data.value?.routes ?? [])

function pickAirport(hit: { code: string; cityName: string; countryCode: string }) {
  countryCode.value = hit.countryCode
  cityName.value = hit.cityName
  airportCode.value = hit.code
  search.value = ''
  debounced.value = ''
}
function pickRoute(sourceId: string) {
  router.push({ name: 'bus', params: { sourceId } })
}
```

`<template>` 内把建议框 `<div v-if="searchHits.length" class="suggest">…</div>` 整块替换为:

```vue
      <div v-if="airportHits.length || routeHits.length" class="suggest">
        <div v-for="hit in airportHits" :key="'ap-' + hit.code" class="suggestItem" tabindex="0" @click="pickAirport(hit)" @keydown.enter="pickAirport(hit)">
          <span class="suggestCode">{{ hit.code }}</span> {{ hit.name }}
          <span class="suggestType">{{ hit.cityName }}</span>
        </div>
        <div v-for="hit in routeHits" :key="'rt-' + hit.sourceId" class="suggestItem" tabindex="0" @click="pickRoute(hit.sourceId)" @keydown.enter="pickRoute(hit.sourceId)">
          <span class="suggestCode">{{ hit.route }}</span> {{ hit.matchedStop }}
          <span class="suggestType">{{ t('home.stopHit') }}</span>
        </div>
      </div>
```

(`home.stopHit` 文案在 Task 7 各语言补;先在 zh-CN/en 里加 `stopHit: '站点'` / `'Stop'` 以免缺键。)

- [ ] **Step 3: 运行前端测试**

Run: `cd frontend && npm test`
Expected: 既有用例通过(HomePage 不再依赖 `searchHits`)。如有 mock 报错,在 `HomePage.spec.ts` 的 `vi.mock('../api/bus', …)` 中补 `search: vi.fn(() => Promise.resolve({ airports: [], routes: [] }))`。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/api/bus.ts frontend/src/pages/HomePage.vue frontend/src/i18n/locales/zh-CN.ts frontend/src/i18n/locales/en.ts frontend/src/test/HomePage.spec.ts
git commit -m "feat(frontend): 搜索框接入 /search,支持按站点搜索(#6)"
```

---

### Task 5: #4 选中机场后该机场全部线路完整列出

**Files:**
- Modify: `frontend/src/pages/HomePage.vue`
- Modify: `frontend/src/test/HomePage.spec.ts`

- [ ] **Step 1: 更新前端用例(默认全列)**

`HomePage.spec.ts` 第 3 例改为断言「选机场后默认渲染全部卡片(此处 1 条)且不依赖默认单选」:

```ts
  it('lists ALL route cards for the picked airport by default', async () => {
    const wrapper = mountHome()
    await flushPromises()
    const selects = wrapper.findAll('.field select')
    await selects[0].setValue('AT')
    await selects[1].setValue('Vienna')
    await selects[2].setValue('VIE')
    await flushPromises()

    expect(wrapper.findAll('.card').length).toBeGreaterThanOrEqual(1)
    const text = wrapper.text()
    expect(text).toContain('VAB 1')
    expect(text).toContain('约 40 分钟')
  })
```

- [ ] **Step 2: HomePage 改为 useQueries 并发取全部详情**

`<script setup>`:删除「默认选中第一条」watch(L70-73)与单条 `detailQ`(L75-80),改为:

```ts
import { useQueries } from '@tanstack/vue-query'

// 选中线路(可选收窄):'' = 全部展示
const routeId = ref('')
watch(airportCode, () => { routeId.value = '' })

// 要展示的线路:未选 = 全部;已选 = 仅该条
const shownBuses = computed(() =>
  routeId.value ? buses.value.filter((b) => b.sourceId === routeId.value) : buses.value,
)

// 并发拉取展示中各线路的详情
const detailQueries = useQueries({
  queries: computed(() =>
    shownBuses.value.map((b) => ({
      queryKey: ['busDetail', b.sourceId],
      queryFn: () => getBusDetail(b.sourceId),
    })),
  ),
})
const details = computed(() =>
  detailQueries.value.map((q) => q.data).filter((d): d is NonNullable<typeof d> => !!d),
)
```

`<template>`:把原「单选 + 单卡」块(L138-148)替换为:

```vue
        <div class="routePick">
          <label>
            <input type="radio" name="route" value="" v-model="routeId" />
            {{ t('home.allRoutes') }}
          </label>
          <label v-for="b in buses" :key="b.sourceId">
            <input type="radio" name="route" :value="b.sourceId" v-model="routeId" />
            {{ b.route }} → {{ b.destination ?? b.route }}
          </label>
        </div>

        <BusCard v-for="d in details" :key="d.sourceId" :bus="d" :detail-link="true" />
```

(`home.allRoutes` 文案 Task 7 补;先在 zh-CN/en 加 `allRoutes: '全部线路'` / `'All routes'`。`detail-link` prop 见 Task 6。)

- [ ] **Step 3: 运行**

Run: `cd frontend && npm test`
Expected: PASS。

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/HomePage.vue frontend/src/test/HomePage.spec.ts frontend/src/i18n/locales/zh-CN.ts frontend/src/i18n/locales/en.ts
git commit -m "feat(frontend): 选中机场后默认列出该机场全部线路卡片(#4)"
```

---

### Task 6: #5 详情页入口 + 面包屑 i18n

**Files:**
- Modify: `frontend/src/components/BusCard.vue`
- Modify: `frontend/src/pages/BusDetailPage.vue`
- Modify: `frontend/src/pages/AirportBusesPage.vue`
- Modify: `frontend/src/i18n/locales/zh-CN.ts` / `en.ts`

- [ ] **Step 1: BusCard 增加可选「查询详情」内部链接**

`BusCard.vue` `<script setup>` 的 props 改为带可选 `detailLink`:

```ts
const props = defineProps<{ bus: BusDetail; detailLink?: boolean }>()
```

页脚 `.updated` 块内、`FreshnessBadge` 之后新增:

```vue
      <router-link
        v-if="detailLink"
        class="detailLink"
        :to="{ name: 'bus', params: { sourceId: bus.sourceId } }"
      >{{ t('detail.viewDetail') }} →</router-link>
```

- [ ] **Step 2: 面包屑改 i18n**

`BusDetailPage.vue`:`<router-link to="/">首页</router-link>` 改 `{{ t('nav.home') }}`,并在 `<script setup>` 加 `import { useI18n } from 'vue-i18n'; const { t } = useI18n()`。
`AirportBusesPage.vue`:同样把「首页」改 `{{ t('nav.home') }}`,「共 {{ data?.length }} 条巴士线路」改 `{{ t('home.routeCount', { count: data?.length ?? 0 }) }}`,并引入 `useI18n`。

- [ ] **Step 3: 文案补键**

`zh-CN.ts`:`detail` 块加 `viewDetail: '查询详情'`;顶层加 `nav: { home: '首页' }`。
`en.ts`:`detail` 块加 `viewDetail: 'View details'`;顶层加 `nav: { home: 'Home' }`。

- [ ] **Step 4: 运行**

Run: `cd frontend && npm test`
Expected: PASS(BusDetailPage.spec / AirportBusesPage.spec 仍过;若断言「首页」字样,文案不变仍命中)。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/BusCard.vue frontend/src/pages/BusDetailPage.vue frontend/src/pages/AirportBusesPage.vue frontend/src/i18n/locales/zh-CN.ts frontend/src/i18n/locales/en.ts
git commit -m "feat(frontend): 首页卡片加「查询详情」入口 + 面包屑 i18n(#5)"
```

---

### Task 7: #8 德语

**Files:**
- Create: `frontend/src/i18n/locales/de.ts`
- Modify: `frontend/src/i18n/index.ts`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 新建 de.ts(对齐 zh-CN/en 全部键,含 Task4/6 新增的 stopHit/allRoutes/nav/viewDetail)**

```ts
export default {
  app: { title: 'Flughafen-Bus-Infos', login: 'Anmelden / Registrieren' },
  nav: { home: 'Startseite' },
  home: {
    kicker: 'Airport Ground Transport',
    title: 'Flughafen-Bus-Infos',
    sub: 'Suchen Sie einen Flughafen oder wählen Sie nacheinander Land, Stadt und Flughafen, um Haltestellen, Fahrpläne, Preise und Hinweise zu sehen. Keine Anmeldung nötig.',
    searchAirport: 'Flughafen suchen',
    searchPlaceholder: 'Flughafen / Stadt / IATA suchen, z. B. Pudong · PVG · Vienna',
    onlyTwoCities: 'Derzeit nur Wien und Shanghai abgedeckt.',
    country: 'Land',
    city: 'Stadt',
    airport: 'Flughafen',
    selectCountry: 'Land wählen',
    selectCity: 'Stadt wählen',
    selectAirport: 'Flughafen wählen',
    official: 'Offizielle Seite ↗',
    routeCount: '{count} Buslinien · eine auswählen',
    pickRoute: 'Wählen Sie eine Linie für Details',
    allRoutes: 'Alle Linien',
    stopHit: 'Haltestelle',
    noBuses: 'Noch keine Buslinien für diesen Flughafen.',
    footer: '// Quelle: offizielle Seiten der Flughäfen & Betreiber · Nur als Referenz · Derzeit Wien und Shanghai',
  },
  detail: {
    destination: 'Ziel', duration: 'Dauer', price: 'Preis', hours: 'Betriebszeiten',
    stops: 'Haltestellen', schedules: 'Fahrplan', images: 'Bilder', files: 'Dateien', official: 'Offizieller Link',
    serviceHead: '🚌 Dauer · Betrieb · Takt', serviceSub: 'Service',
    schedAllDay: 'Ganztägig', noSchedule: 'Keine Fahrplandaten',
    viewDetail: 'Details ansehen',
  },
  state: { loading: 'Wird geladen…', error: 'Laden fehlgeschlagen, bitte erneut versuchen', empty: 'Keine Daten' },
  freshness: { updated: 'Zuletzt aktualisiert', fetchFailed: 'Abruf fehlgeschlagen, Infos evtl. veraltet' },
}
```

- [ ] **Step 2: 注册 de + 放宽类型**

`i18n/index.ts`:

```ts
import de from './locales/de'
// fallback 计算不变
export const i18n = createI18n({
  legacy: false,
  locale: saved ?? fallback,
  fallbackLocale: 'en',
  messages: { 'zh-CN': zhCN, en, de },
})

export function setLocale(locale: 'zh-CN' | 'en' | 'de') {
  i18n.global.locale.value = locale
  localStorage.setItem('locale', locale)
}
```

- [ ] **Step 3: App.vue 加 DE 按钮**

`.lang` 组里 EN 按钮后新增:

```vue
          <button :aria-pressed="locale === 'de'" @click="setLocale('de')">DE</button>
```

- [ ] **Step 4: 同步给 zh-CN.ts / en.ts 补齐 Task4/6 缺的键**

确认 `zh-CN.ts` / `en.ts` 已含 `nav.home`、`home.allRoutes`、`home.stopHit`、`detail.viewDetail`(Task 4/6 已加;此步只做核对,缺则补)。

- [ ] **Step 5: 运行 + 类型检查**

Run: `cd frontend && npm test && npx vue-tsc -b --noEmit`
Expected: PASS(三语言键齐全,无类型错误)。

- [ ] **Step 6: Commit**

```bash
git add frontend/src/i18n frontend/src/App.vue
git commit -m "feat(frontend): 新增德语(de)语言包与切换按钮(#8)"
```

---

### Task 8: 自动测试(opencli)+ 全量回归 + fix.md

**Files:**
- Modify: `fix.md`(追加 #3 起的修复记录)

- [ ] **Step 1: 修 opencli 权限**

`~/.opencli` 因属主/权限导致 `EACCES`。修复(必要时改属主):

```bash
sudo chown -R "$(whoami)" ~/.opencli 2>/dev/null; chmod -R u+rwX ~/.opencli; opencli --help | head -20
```

Expected: 不再报 `EACCES`,打印帮助。

- [ ] **Step 2: 后端全量测试(需 Docker)**

Run: `cd backend && mvn -q test`
Expected: 全绿。

- [ ] **Step 3: 前端全量测试 + 构建**

Run: `cd frontend && npm test && npm run build`
Expected: 全绿,构建成功。

- [ ] **Step 4: 起服务 + opencli 端到端**

`docker compose up -d --build` 起栈(若 Docker Hub 限流见 fix.md #1),用 opencli 跑端到端:验证 ① 选机场后多卡全列 ② 站点搜索命中 ③ 卡片「查询详情」跳详情页 ④ 切到 DE 文案变德语 ⑤ 详情站点首节点是机场。具体命令依 Step 1 打印的 opencli 用法而定。

- [ ] **Step 5: 记录 fix.md**

为本次每项修复在 `fix.md` 追加「现象 → 根因 → 修复 → 验证」。

- [ ] **Step 6: Commit**

```bash
git add fix.md
git commit -m "docs(fix): 记录查询主线 5 项修复与验证"
```

---

## Self-Review

- **Spec 覆盖:** #1→Task1/2;#4→Task5;#5→Task6;#6→Task3/4;#8→Task7;验证→Task8。全覆盖。
- **占位符:** 无 TBD/TODO;每步给出可运行代码或命令。Task8 Step4 的 opencli 具体子命令依工具实际帮助而定(工具未知,合理)。
- **类型一致:** `AirportStopOrderer.airportFirst`、`SearchResultDto.{AirportHit,RouteHit}`、`service.search`、前端 `search`/`SearchResult`、`BusCard` 的 `detailLink` prop、i18n 键(`nav.home`/`home.allRoutes`/`home.stopHit`/`detail.viewDetail`)在各任务间命名一致。
- **顺序:** 新 i18n 键在首次引用的任务(4/6)即加入 zh-CN/en,Task7 补 de 并核对,避免缺键。
