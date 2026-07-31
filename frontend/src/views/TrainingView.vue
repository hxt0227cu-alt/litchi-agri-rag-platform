<template>
  <div class="page-shell training-page">
    <section class="hero glass-card">
      <div class="hero-copy">
        <span class="hero-kicker">Farmer Classroom</span>
        <h3 class="section-title">农户学习课堂</h3>
        <p class="section-copy">
          这里不只讲“这是什么病”，还会把农户真正会遇到的判断链路讲完整: 先看症状和发生阶段，再看拍照位置、
          雨季环境、花果期管理和安全边界，最后再进入病害识别、智能问答和方案选择。
        </p>

        <div class="hero-signals">
          <article class="hero-signal-card">
            <span>课堂模块</span>
            <strong>{{ modules.length }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>巡园清单</span>
            <strong>{{ fieldChecklist.length }}</strong>
          </article>
          <article class="hero-signal-card">
            <span>课堂出口</span>
            <strong>识别 + 问答</strong>
          </article>
        </div>
      </div>

      <div class="hero-actions">
        <el-button type="primary" @click="goTo('/diagnosis')">去病害识别</el-button>
        <el-button @click="goTo('/chat')">去智能问答</el-button>
        <el-button @click="goTo('/knowledge')">查看研判图谱</el-button>
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <div class="metric-label">拍照重点</div>
        <div class="metric-value">清楚</div>
        <div class="metric-note">优先拍果面、叶面、病斑边缘和花穗位置，让系统能分辨症状阶段，而不只是看远景。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">判断顺序</div>
        <div class="metric-value">先现象</div>
        <div class="metric-note">先看病斑、霉层、虫孔和落果，再结合雨季、花果期和果园通风情况判断原因。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">雨季边界</div>
        <div class="metric-value">先复查</div>
        <div class="metric-note">连续降雨后先做排水、通风、清园和雨后复查，再去看药剂或门店方案。</div>
      </article>
      <article class="metric-card">
        <div class="metric-label">课堂出口</div>
        <div class="metric-value">能落地</div>
        <div class="metric-note">学完后可以直接去识别、问答、图谱或方案页，避免学完和实操脱节。</div>
      </article>
    </section>

    <section class="module-grid">
      <article v-for="module in modules" :key="module.title" class="soft-card module-card">
        <span class="module-kicker">{{ module.kicker }}</span>
        <h3>{{ module.title }}</h3>
        <p class="module-copy">{{ module.copy }}</p>
        <ul class="bullet-list">
          <li v-for="point in module.points" :key="point">{{ point }}</li>
        </ul>
      </article>
    </section>

    <section class="detail-grid">
      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">田间速查清单</h3>
            <p class="section-copy">巡园时照着这张清单走，课堂内容会更容易跟真实场景对上。</p>
          </div>
        </header>

        <div class="tip-list">
          <article v-for="item in fieldChecklist" :key="item" class="tip-card">{{ item }}</article>
        </div>
      </article>

      <article class="soft-card panel">
        <header class="panel-header">
          <div>
            <h3 class="section-title">常见误判提醒</h3>
            <p class="section-copy">很多回答出错，不是系统不会答，而是前面的判断对象和阶段就偏了。</p>
          </div>
        </header>

        <div class="warning-list">
          <article v-for="item in commonMistakes" :key="item.title" class="warning-card">
            <strong>{{ item.title }}</strong>
            <p>{{ item.copy }}</p>
          </article>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const modules = [
  {
    kicker: '病症入门',
    title: '常见病虫害先看什么',
    copy: '课堂先帮农户建立最基础的辨别框架，避免一上来就把零散症状当成最终结论。',
    points: [
      '炭疽病重点看圆形或近圆形褐色病斑、果实腐烂和雨季高湿环境。',
      '霜疫霉病重点看花穗、幼果阶段和病果表面的白色霉层。',
      '蒂蛀虫要重点看花穗、果梗、虫孔、落果和高发期的巡园频率。'
    ]
  },
  {
    kicker: '拍照识别',
    title: '怎么拍，识别结果更准',
    copy: '好照片不是拍得多，而是拍得对。课堂会把“拍哪、什么时候拍、要不要补拍”讲明白。',
    points: [
      '优先补拍病斑边缘、果柄、花穗和整枝位置，方便判断扩展方向。',
      '连续阴雨后要先擦干镜头，避免水珠、反光和模糊影响识别。',
      '同一棵树至少保留近景和中景两类画面，便于比对病害范围。'
    ]
  },
  {
    kicker: '花果期管理',
    title: '桂味等品种在花果期怎么巡园',
    copy: '农户最容易忽视的是物候期差异。花穗、幼果、膨果和转色前后的重点并不一样。',
    points: [
      '花果期先稳树势、看通风透光和雨后积水，再谈后续用药或保果。',
      '连续降雨时要同步关注炭疽病、霜疫霉病和蒂蛀虫，不要只盯一种症状。',
      '病果、虫果和落果要及时清理，减少园内持续传播和再次侵染。'
    ]
  },
  {
    kicker: '雨季管理',
    title: '雨季先做哪几件事',
    copy: '课堂会把“排水、通风、清园、复查”放在前面，先把农户最需要落地的动作理顺。',
    points: [
      '连续降雨后先看排水、枝叶郁闭和果园湿度，不要急着只盯药剂名称。',
      '初发阶段先做清理和复查，再结合标签和安全间隔期看后续方案。',
      '对雨季高发病害，提前巡园和保护性管理通常比事后补救更重要。'
    ]
  },
  {
    kicker: 'AI 阅读',
    title: '怎么读懂 AI 给出的回答',
    copy: '把回答拆成“依据、建议、提醒”三段来看，能大幅降低误用系统建议的风险。',
    points: [
      '先看回答有没有明确提到症状依据、发生阶段或环境条件。',
      '再看建议是否可执行，是否真能回到巡园、复查、清理这些动作上。',
      '最后看有没有风险提醒，尤其是标签边界、安全间隔期和需要人工复核的情况。'
    ]
  },
  {
    kicker: '安全边界',
    title: '进入方案前先确认什么',
    copy: '识别和问答之后不要急着照搬处置，先把标签、阶段、风险和复核条件确认清楚。',
    points: [
      '先核对作物阶段、病虫害标签和图片症状是否一致，避免把相似症状带入错误方案。',
      '再关注药剂标签、安全间隔期和采收时间，不能只看推荐摘要就直接处理。',
      '遇到大面积暴发、连续误判或高风险用药场景，要转向门店方案或管理员人工复核。'
    ]
  }
]

const fieldChecklist = [
  '巡园先看地面积水、树冠通风和花穗、幼果是否有连片异常。',
  '拍照时把病斑边缘和整枝位置一起拍，避免只有局部特写。',
  '雨后先复查果面和叶片，再判断是不是进入方案推荐。',
  '遇到白色霉层、虫孔、落果并发时，先记录发生阶段再提问。',
  '花果期不要只看病斑本身，也要看果梗、花穗和树势变化。',
  '看 AI 回答时，把“依据”和“下一步动作”两部分单独记下来。'
]

const commonMistakes = [
  {
    title: '把霜疫霉病和炭疽病混成一种',
    copy: '看到褐斑就直接下结论很容易出错。霜疫霉病更要看白色霉层和花穗、幼果阶段，炭疽病更常见圆形病斑和果实腐烂。'
  },
  {
    title: '只拍一张远景就去识别',
    copy: '系统识别不到病斑细节时，后面的问答和方案推荐都会变钝。近景、中景、病斑边缘最好都保留。'
  },
  {
    title: '把 AI 提醒直接当成完整处方',
    copy: '课堂会强调: 问答页是判断与解释入口，不替代标签、门店方案和人工复核。'
  }
]

const goTo = (path: string) => {
  router.push(path)
}
</script>

<style scoped>
.training-page {
  gap: 18px;
}

.hero,
.panel,
.module-card {
  padding: 24px;
}

.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
  align-items: center;
}

.hero-copy {
  display: grid;
  gap: 18px;
}

.hero-kicker,
.module-kicker {
  display: inline-flex;
  width: fit-content;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(242, 140, 40, 0.14);
  color: #9c4e0c;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-signals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.hero-signal-card {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.66);
  border: 1px solid rgba(34, 53, 47, 0.08);
}

.hero-signal-card span {
  color: var(--ink-soft);
  font-size: 13px;
}

.hero-signal-card strong {
  display: block;
  margin-top: 8px;
  color: var(--ink-strong);
  font-size: 24px;
}

.hero-actions {
  display: grid;
  gap: 14px;
}

.module-grid,
.detail-grid {
  display: grid;
  gap: 18px;
}

.module-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.detail-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.module-card h3 {
  margin: 14px 0 0;
  color: var(--ink-strong);
}

.module-copy {
  margin: 14px 0 0;
  color: var(--ink-soft);
  line-height: 1.75;
}

.bullet-list {
  display: grid;
  gap: 10px;
  margin: 16px 0 0;
  padding-left: 18px;
  color: var(--ink-strong);
  line-height: 1.8;
}

.panel-header {
  margin-bottom: 18px;
}

.tip-list,
.warning-list {
  display: grid;
  gap: 12px;
}

.tip-card,
.warning-card {
  padding: 16px 18px;
  border-radius: 18px;
  border: 1px solid rgba(34, 53, 47, 0.08);
  background: rgba(255, 255, 255, 0.78);
}

.tip-card {
  color: var(--ink-soft);
  line-height: 1.75;
}

.warning-card strong {
  color: var(--ink-strong);
}

.warning-card p {
  margin: 10px 0 0;
  color: var(--ink-soft);
  line-height: 1.75;
}

@media (max-width: 1180px) {
  .hero,
  .hero-signals,
  .module-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
