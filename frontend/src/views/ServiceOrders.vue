<template>
  <div class="service-orders">
    <header class="orders-header">
      <div>
        <span class="eyebrow">{{
          admin ? "OPERATIONS / 服务运营" : "MY SERVICES / 服务订单"
        }}</span>
        <h1>{{ admin ? "服务订单与对账" : "我的服务订单" }}</h1>
        <p>
          {{
            admin
              ? "只在核实处理结果后操作不确定订单；每笔处理都有账本与操作记录。"
              : "进度、计划和售后都在这里。结果不确定时请勿重复下单。"
          }}
        </p>
      </div>
      <el-button :loading="loading" :disabled="!sessionActive" @click="load">刷新列表</el-button
      ><el-button v-if="!admin" type="primary" @click="router.push(serviceDestination(applied.providerType) || '/services')"
        >购买服务</el-button
      >
    </header>
    <ServiceOrderFilters v-if="sessionActive" v-model="draft" :admin="admin" :loading="loading"
      :dirty="dirty" :validation="validation" @submit="submit" @reset="reset" />
    <section v-if="hasFocus" class="order-focus" aria-label="订单定位">
      <div><strong>查看指定订单</strong><p>当前只查询这笔订单，可返回完整列表继续查找。</p></div>
      <el-button @click="clearFocus">返回全部订单</el-button>
    </section>
    <div v-if="filterSummary.length" class="applied-filters" aria-label="已应用筛选">
      <span>已应用：</span><el-tag v-for="text in filterSummary" :key="text" type="info">{{ text }}</el-tag>
    </div>
    <div class="search-status" role="status" aria-live="polite">
      <span v-if="!sessionActive">请登录后查看服务订单。</span>
      <span v-else-if="loading">正在查询订单…</span>
      <span v-else-if="result">找到 {{ total }} 笔订单<span v-if="items.length"> · 第 {{ page }} 页</span></span>
      <span v-if="total > 200000">结果较多，请缩小筛选范围查看更早订单。</span>
    </div>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon>
      <el-button :disabled="dirty || loading" @click="retry">重试查询</el-button>
      <p v-if="dirty">筛选条件已修改，请点击“查询订单”。</p>
    </el-alert>
    <div v-loading="loading" class="order-list">
      <article
        v-for="item in items"
        :key="item.id"
        class="order-card"
        :class="{ focused: viewQuery.focus === item.id, 'leidian-order': isLeidianService(item), 'jingyu-order': isJingyuService(item) }"
      >
        <div class="order-title">
          <div>
            <small
              >{{ serviceNames[item.providerType] }} /
              {{ item.createTime?.replace("T", " ") }}</small
            >
            <h2>{{ item.title }}</h2>
          </div>
          <el-tag :type="tagType(item.status, item.providerType)">{{
            orderStateName(item)
          }}</el-tag>
        </div>
        <div class="order-metrics">
          <div>
            <span>服务账号</span><strong>{{ item.accountLabel }}</strong>
          </div>
          <div>
            <span>{{
              item.providerType === "sxdk_tw"
                ? "累计已购服务日"
                : item.providerType === "appui" ? "已使用 / 已购天数" : isLeidianService(item) ? "已使用 / 已购次数" : isTotalDistanceService(item) ? "订单数量" : "完成 / 总次数"
            }}</span
            ><strong
              >{{
                item.providerType === "sxdk_tw" || isTotalDistanceService(item)
                  ? item.quantity
                  : (item.completed ?? "—")
              }}
              <small>{{
                item.providerType === "sxdk_tw" ? "天" : item.providerType === "appui" ? `/ ${item.quantity} 天` : isTotalDistanceService(item) ? "单" : `/ ${item.quantity}`
              }}</small></strong
            >
          </div>
          <div>
            <span>{{
              item.providerType === "sxdk_tw" ? "服务截止日" : item.providerType === "appui" ? "最近核对剩余" : isTotalDistanceService(item) ? "总公里数" : "每次距离"
            }}</span
            ><strong
              >{{
                item.providerType === "sxdk_tw"
                  ? item.schedule?.endDate
                  : item.providerType === "appui" ? appuiRemainingText(item) : item.distance
              }}
              <small v-if="!['sxdk_tw', 'appui'].includes(item.providerType)">公里</small></strong
            >
          </div>
          <div>
            <span>已扣款 / 已退款</span
            ><strong
              >¥{{ moneyText(item.paidAmount) }}
              <small>/ ¥{{ moneyText(item.refundedAmount) }}</small></strong
            >
          </div>
        </div>
        <el-progress
          v-if="item.completed != null && !['sxdk_tw', 'appui', 'leidian'].includes(item.providerType) && !isTotalDistanceService(item)"
          :percentage="
            Math.min(100, Math.round((item.completed / item.quantity) * 100))
          "
          :show-text="false"
          :stroke-width="5"
        />
        <p
          v-if="item.completed == null && !['sxdk_tw', 'appui', 'leidian'].includes(item.providerType) && !isTotalDistanceService(item)"
          class="unknown-progress"
        >
          当前订单只返回状态，未返回完成次数；具体执行情况请查看执行记录。
        </p>
        <template v-if="isTotalDistanceService(item)">
          <TotalDistancePlanSummary v-if="item.distancePlan" :plan="item.distancePlan" />
          <p class="unknown-progress">仅提供提交状态，暂无单笔完成进度；状态更新不代表执行完成，不会自动退款。</p>
        </template>
        <p v-if="item.providerType === 'sxdk_tw'" class="unknown-progress">
          {{ internshipCalendarText(item.schedule) }} ·
          {{ item.status === "COMPLETED"
            ? "服务期结束不代表考勤已完成；具体结果请查看执行记录。"
            : "执行结果请查看订单记录，日历不代表考勤已完成。" }}
        </p>
        <p v-if="isLeidianService(item)" class="unknown-progress">已使用次数是已消耗的服务量，不代表跑步或成绩完成；请按需查看执行记录和成绩查询信息。</p>
        <ServiceStatusCheck :check="item.statusCheck" :provider-type="item.providerType" />
        <el-alert
          v-if="item.pendingOperationId"
          title="上一笔操作仍待确认：扣款和订单已保存，请先检查结果，不要再次购买。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-else-if="item.status === 'REFUND_REVIEW'"
          title="退款状态已更新，余额尚未入账，请联系管理员核对。"
          type="warning"
          :closable="false"
        />
        <footer>
          <span class="order-number">{{ item.id }}</span>
          <div class="order-actions">
            <template v-if="!admin"
              ><el-button
                v-if="item.pendingOperationId"
                :loading="busyId === item.id"
                @click="checkPending(item)"
                >检查提交结果</el-button
              ><el-button
                v-else-if="!isLeidianService(item) || canReadLeidianRecord(item)"
                :disabled="['CANCELLED', 'REFUNDED'].includes(item.status)"
                :loading="busyId === item.id"
                @click="sync(item)"
                >{{ item.providerType === "sxdk_tw" ? "核对计划状态" : isTotalDistanceService(item) ? "核对提交状态" : serviceActionName("SYNC", item) }}</el-button
              ><el-button @click="showEvents(item)">操作记录</el-button
              ><el-button v-if="item.providerType === 'sxdk_tw'" @click="notificationOrder=item; notificationOpen=true">微信通知</el-button
              ><el-button
                v-if="
                  item.providerType !== 'heisha' && !isTotalDistanceService(item) && !item.pendingOperationId &&
                  (!isLeidianService(item) || canReadLeidianRecord(item))
                "
                @click="showRunLogs(item)"
                >执行记录</el-button
              ><el-button
                v-for="action in item.actions.filter(
                  (a) => !['CHANGE_TIME', 'DELAY_TASK'].includes(a) && (!isLeidianService(item) || canReadLeidianRecord(item)),
                )"
                :key="action"
                :type="['REFUND', 'CANCEL'].includes(action) ? 'danger' : 'primary'"
                plain
                :loading="busyId === item.id"
                @click="prepareAction(item, action)"
                >{{ serviceActionName(action, item) }}</el-button
              ></template
            >
            <template v-else>
              <el-button @click="showAudit(item)">核对资料与记录</el-button>
              <el-button
                v-if="item.pendingOperationId"
                type="warning"
                @click="openResolve(item)"
                >核对处理结果</el-button
              >
              <el-button
                v-else-if="item.status === 'REFUND_REVIEW' && !isTotalDistanceService(item)"
                type="warning"
                @click="openSettlement(item)"
                >核对退款入账</el-button
              >
            </template>
          </div>
        </footer>
      </article>
    </div>
    <el-empty v-if="result && !loading && !error && !items.length" :description="emptyDescription" />
    <el-pagination
      v-if="result && !loading && !error && total > 20"
      :current-page="page"
      :page-size="20"
      :total="Math.min(total, 200000)"
      layout="prev, pager, next"
      aria-label="订单分页"
      @current-change="goToPage"
    />
    <ServiceNotifications v-if="!admin" v-model="notificationOpen" :order="notificationOrder" />
    <ServiceScoreInfo v-if="!admin" v-model="scoreOpen" :order="scoreOrder" />
    <LeidianTaskTimeEditor v-if="!admin && timeTask?.providerType !== 'jingyu'" v-model="taskTimeOpen" :task="timeTask" @quote="taskTimeQuote" />
    <JingyuTaskTimeEditor v-if="!admin && timeTask?.providerType === 'jingyu'" v-model="taskTimeOpen" :task="timeTask" @quote="taskTimeQuote" />
    <ServiceQuoteConfirm
      :quote="quote"
      :administrative="quote?.action === 'SETTLE_REFUND'"
      :provider-type="items.find(item => item.id === quote?.orderId)?.providerType"
      @close="quote = null"
      @result="onQuoteResult"
    />
    <el-drawer
      v-model="eventsOpen"
      title="订单操作记录"
      size="min(520px, 100vw)"
      ><el-timeline
        ><el-timeline-item
          v-for="event in events"
          :key="event.id"
          :timestamp="event.createTime?.replace('T', ' ')"
          ><b>{{ serviceActionName(event.action, eventsOrder) }} · {{ stateName(event.state) }}</b>
          <p>¥{{ event.amount }}</p>
          <small class="order-number">{{ event.id }}</small>
          <p v-if="event.errorCategory">
            结果尚需核对；此记录未自动重试。
          </p></el-timeline-item
        ></el-timeline
      ></el-drawer
    >
    <el-drawer
      v-model="runLogsOpen"
      class="service-run-logs"
      title="执行记录"
      size="min(520px, 100vw)"
      ><p v-if="runLogsLoading" role="status">正在读取第 {{ runLogPage }} 页执行记录…</p>
      <el-alert v-if="runLogsError" type="warning" :closable="false" :title="runLogsError">
        <el-button @click="changeLogPage(0)">重试读取</el-button>
      </el-alert>
      <el-alert
        v-if="runLogOrder?.providerType === 'sxdk_tw'"
        type="info"
        :closable="false"
        class="log-privacy-note"
        title="仅展示最近十条记录的时间与类别，不代表执行成功。为保护账号信息，不展示原始日志。" /><el-timeline
        ><el-timeline-item
          v-for="log in runLogs.items"
          :key="log.id"
          :timestamp="log.time"
          >{{ log.status
          }}<el-button
            v-if="
              (runLogOrder?.providerType === 'flash' && ['未开始', '需要处理'].includes(log.status)) ||
              (isLeidianService(runLogOrder) && log.editable === true && canReadLeidianRecord(runLogOrder) && runLogOrder.actions.includes('CHANGE_TIME')) ||
              jingyuTaskEditable(runLogOrder, runLogs, log, 'CHANGE_TIME')
            "
            text
            type="primary"
            :disabled="runLogsLoading || !!busyId"
            @click="editTaskTime(log)"
            >修改时间</el-button
          ><el-button
            v-if="
              (runLogOrder?.providerType === 'flash' &&
              runLogOrder.actions?.includes('DELAY_TASK') &&
              ['未开始', '需要处理'].includes(log.status)) ||
              jingyuTaskEditable(runLogOrder, runLogs, log, 'DELAY_TASK')
            "
            text
            type="primary"
            :loading="busyId === runLogOrder?.id"
            @click="delayTask(log)"
            >延期此任务</el-button>
            <p v-if="(isLeidianService(runLogOrder) || isJingyuService(runLogOrder)) && log.endTime" class="log-end-time">结束时间：{{ log.endTime }}（北京时间）</p>
          </el-timeline-item
        ></el-timeline
      >
      <p v-if="isLeidianService(runLogOrder)" class="unknown-progress">第 {{ runLogPage }} 页 · 时间均为北京时间，记录状态不等同于成绩确认。</p>
      <p v-if="isJingyuService(runLogOrder)" class="unknown-progress">第 {{ runLogPage }} 页 · 时间均为北京时间。任务显示“已退款”不代表余额已到账，请查看订单退款金额。</p>
      <div class="order-actions">
        <el-button
          :disabled="runLogPage <= 1 || runLogsLoading || !!busyId"
          @click="changeLogPage(-1)"
          >上一页</el-button
        ><el-button
          :disabled="!runLogs.hasMore || runLogsLoading || !!busyId || (isLeidianService(runLogOrder) && runLogPage >= 100) || (isJingyuService(runLogOrder) && runLogPage >= 19)"
          @click="changeLogPage(1)"
          >下一页</el-button
        >
      </div>
      <el-empty v-if="!runLogsLoading && !runLogsError && !runLogs.items.length" description="暂无执行记录"
    /></el-drawer>
    <el-dialog
      v-model="internshipOpen"
      class="internship-editor"
      title="编辑实习周期 / 续期"
      width="min(760px,96vw)"
      :close-on-click-modal="false"
      :show-close="!internshipLoading"
      ><el-form label-position="top" :disabled="internshipLoading"
        ><InternshipPlanFields
          v-if="internshipOrder && internshipSchedule"
          v-model="internshipForm"
          v-model:schedule="internshipSchedule"
          :project="internshipOrder.project"
          editing
          authorized /></el-form
      ><template #footer
        ><el-button
          :disabled="internshipLoading"
          @click="internshipOpen = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="internshipLoading"
          @click="previewInternship"
          >预览续期金额</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="reportOpen"
      title="补交本人已授权的记录"
      width="min(520px,94vw)"
      :close-on-click-modal="false"
      ><el-alert
        type="info"
        :closable="false"
        title="仅可补交已购买且不晚于今天的服务日；范围内不能跳过未购日期，请分段提交。"
      /><el-form label-position="top" class="resolve-form"
        ><el-form-item label="补交类型"
          ><el-select v-model="reportForm.reportType"
            ><el-option
              v-for="type in internshipReportTypes(reportOrder?.project)"
              :key="type"
              :label="type"
              :value="type" /></el-select></el-form-item
        ><el-form-item label="开始日期"
          ><el-date-picker
            v-model="reportForm.startDate"
            :disabled-date="reportDateDisabled"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="结束日期"
          ><el-date-picker
            v-model="reportForm.endDate"
            :disabled-date="reportDateDisabled"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="reportOpen = false">取消</el-button
        ><el-button
          type="primary"
          :loading="internshipLoading"
          @click="previewReport"
          >预览本次操作</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="planOpen"
      :title="['appui', 'leidian'].includes(planOrder?.providerType) ? '编辑执行安排' : '编辑执行计划'"
      class="service-plan-editor"
      width="min(620px, 95vw)"
      :close-on-click-modal="false"
      :close-on-press-escape="!planLoading"
      :show-close="!planLoading"
    >
      <el-form v-if="planOptions" label-position="top" :disabled="planLoading">
        <template v-if="planOrder?.providerType === 'appui'">
          <el-alert :title="planOptions.notice" type="info" :closable="false" />
          <AppuiPlanFields v-model="planFields" />
          <el-form-item label="新密码（选填）">
            <el-input v-model="planFields.password" type="password" show-password autocomplete="new-password"
              aria-label="新密码（选填）" maxlength="128" placeholder="留空则保持原密码" />
          </el-form-item>
          <p v-if="appuiPlanError(planFields)" class="unknown-progress">{{ appuiPlanError(planFields) }}</p>
        </template>
        <template v-else-if="isLeidianService(planOrder)">
          <el-alert title="这里读取的是下单时保存的安排，修改后的实际时间以执行记录为准。修改不会增加次数或额外扣款。" type="info" :closable="false" />
          <LeidianPlanFields v-model="planFields" :disabled="planLoading" />
          <p v-if="leidianPlanError(planFields)" class="unknown-progress" role="status">{{ leidianPlanError(planFields) }}</p>
        </template>
        <template v-else><el-form-item
          v-for="field in [...new Set(planOptions.choices.map((c) => c.field))]"
          :key="field"
          :label="fieldNames[field] || field"
          ><el-select v-model="planFields[field]"
            ><el-option
              v-for="choice in planOptions.choices.filter(
                (c) => c.field === field,
              )"
              :key="choice.value"
              :value="choice.value"
              :label="choice.label" /></el-select></el-form-item
        ><el-form-item label="每次距离（公里）"
          ><el-input
            v-model="planFields.distance"
            inputmode="decimal" /></el-form-item
        ><WuxinPlanFields v-model="planFields" :with-start-date="false"
      /></template></el-form>
      <template #footer
        ><el-button :disabled="planLoading" @click="planOpen = false">取消</el-button
        ><el-button type="primary" :loading="planLoading"
          :disabled="(planOrder?.providerType === 'appui' && !!appuiPlanError(planFields)) || (isLeidianService(planOrder) && !!leidianPlanError(planFields))" @click="previewPlan"
          >预览修改</el-button
        ></template
      >
    </el-dialog>
    <el-drawer
      v-model="auditOpen"
      title="核对资料与审计记录"
      size="min(560px, 100vw)"
    >
      <template v-if="audit"
        ><el-descriptions :column="1" border
          ><el-descriptions-item label="所属用户">{{
            audit.userId
          }}</el-descriptions-item
          ><el-descriptions-item label="接口配置编号">{{
            audit.providerId
          }}</el-descriptions-item
          ><el-descriptions-item label="服务订单号"
            ><span class="order-number">{{
              audit.externalOrderNo || "尚未确认，请查询订单记录"
            }}</span></el-descriptions-item>
          <el-descriptions-item v-if="isLeidianService(audit.order)" label="订单记录编号"><span class="order-number">{{ audit.externalSubOrderNo || "尚未确认，请核对独立记录编号" }}</span></el-descriptions-item>
          </el-descriptions
        >
        <el-timeline class="resolve-form"
          ><el-timeline-item
            v-for="entry in audit.events"
            :key="entry.operation.id"
            :timestamp="entry.operation.createTime?.replace('T', ' ')"
            ><b
              >{{ serviceActionName(entry.operation.action, audit.order) }} ·
              {{ stateName(entry.operation.state) }}</b
            >
            <p>¥{{ moneyText(entry.operation.amount) }}</p>
            <small v-if="entry.resolvedBy"
              >核对人 #{{ entry.resolvedBy }}</small
            >
            <p v-if="entry.evidence" class="audit-evidence">
              {{ entry.evidence }}
            </p></el-timeline-item
          ></el-timeline
        ></template
      >
    </el-drawer>
    <el-dialog
      v-model="settlementOpen"
      title="核对主动退款"
      class="service-settlement"
      :close-on-press-escape="!settlementLoading"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
      :show-close="!settlementLoading"
    >
      <template v-if="settlementOrder"
        ><el-alert
          type="warning"
          title="只为已经核实的退款入账。不再次取消订单，也不把错误或超时当成退款。"
          :closable="false"
        />
        <p class="order-number">
          服务订单号 {{ settlementAudit?.externalOrderNo || "未取得" }} · 接口
          #{{ settlementAudit?.providerId }}
        </p>
        <p v-if="settlementOrder.completed == null" class="unknown-progress">
          {{ isLeidianService(settlementOrder) ? "尚未取得已使用次数，请核实实际可退次数后填写，默认不退款。" : settlementOrder.providerType === "appui"
            ? "尚未取得已使用天数，请核实实际可退天数后填写，默认不退款。"
            : "当前订单未提供完成次数，不能将总次数视为剩余次数。请核实实际可退次数后填写，默认不退款。" }}
        </p>
        <el-form
          label-position="top"
          class="resolve-form"
          :disabled="settlementLoading"
          ><el-form-item
            :label="settlementOrder.providerType === 'appui' ? `实际核实的退款天数（最多 ${maxRefundableUnits(settlementOrder)} 天）` : `实际核实的退款次数（最多 ${maxRefundableUnits(settlementOrder)} 次）`"
            ><el-input-number
              v-model="settlement.refundedUnits"
              :min="0"
              :max="maxRefundableUnits(settlementOrder)"
              :precision="0" /></el-form-item
          ><el-form-item label="核对依据（至少 10 字，不要填写密码或密钥）"
            ><el-input
              v-model="settlement.evidence"
              type="textarea"
              :rows="3"
              maxlength="1000" /></el-form-item
          ><el-checkbox v-model="settlement.upstreamChecked"
            >我已核查退款单与资金流水</el-checkbox
          ></el-form
        ></template
      >
      <template #footer
        ><el-button
          :disabled="settlementLoading"
          @click="settlementOpen = false"
          >取消</el-button
        ><el-button
          type="primary"
          :loading="settlementLoading"
          :disabled="
            !settlement.upstreamChecked ||
            settlement.evidence.trim().length < 10 || !!jingyuSettlementError
          "
          @click="previewSettlement"
          >预览退款金额</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="resolveOpen"
      class="service-resolution"
      :show-close="!resolving" :close-on-press-escape="!resolving"
      title="人工核对 · 会影响订单与余额"
      width="min(560px, 94vw)"
      :close-on-click-modal="false"
    >
      <el-alert
        type="warning"
        title="必须先核对订单和资金流水。不能只凭超时或错误提示退回余额。"
        :closable="false"
      />
      <el-form v-if="resolveQuote" label-position="top" class="resolve-form" :disabled="resolving"
        ><p>
          {{ serviceActionName(resolveQuote.action, resolveOrder || resolveQuote) }} · ¥{{ resolveQuote.amount }} ·
          {{ stateName(resolveQuote.state) }}
        </p>
        <el-form-item label="已核实的处理结果"
          ><el-radio-group v-model="resolution.outcome"
            ><el-radio value="ACCEPTED">{{ resolveQuote.distancePlan ? "已受理提交记录（不确认执行完成）" : "已受理" }}</el-radio
            ><el-radio value="NOT_ACCEPTED"
              >{{ /^0(?:\.0+)?$/.test(resolveQuote.amount) ? "未受理，保留原订单" : "未受理，退回本次扣款" }}</el-radio
            ></el-radio-group
          ></el-form-item
        ><el-form-item
          v-if="
            resolution.outcome === 'ACCEPTED' &&
            resolveQuote.action === 'CREATE'
          "
          label="已核实的服务订单号"
          ><el-input
            v-model="resolution.externalOrderNo"
            aria-label="已核实的服务订单号"
            maxlength="64" /></el-form-item
        ><el-form-item v-if="(isLeidianService(resolveOrder) || isJingyuService(resolveOrder)) && resolution.outcome === 'ACCEPTED' && resolveQuote.action === 'CREATE'"
          label="已核实的订单记录编号">
          <el-input v-model="resolution.externalSubOrderNo" aria-label="已核实的订单记录编号" inputmode="numeric" maxlength="19" />
        </el-form-item>
        <p v-if="isLeidianService(resolveOrder) && resolveQuote.action === 'CANCEL' && resolution.outcome === 'ACCEPTED'" class="unknown-progress">取消受理后仍需单独核对退款，不会在这里退回余额。</p>
        <el-form-item
          v-if="
            resolution.outcome === 'ACCEPTED' &&
            resolveQuote.action === 'REFUND'
          "
          :label="resolveQuote.quantityUnit === '天' ? '实际退回的剩余天数' : '实际退回的剩余次数'"
          ><el-input-number
            v-model="resolution.refundedUnits"
            :min="0"
            :max="isJingyuService(resolveOrder) ? resolveQuote.quantity : 9999"
            :precision="0" /></el-form-item
        ><p v-if="isJingyuService(resolveOrder) && resolveQuote.action === 'REFUND' && resolution.outcome === 'ACCEPTED'" class="unknown-progress">请填写已核实的确切退款次数；不会使用预估次数代填。核对完成前，余额不会入账。</p>
        <el-form-item label="核对依据（至少 10 字，不要填写密码或密钥）"
          ><el-input
            v-model="resolution.evidence"
            type="textarea"
            :rows="3"
            maxlength="1000" /></el-form-item
        ><el-checkbox v-model="resolution.upstreamChecked"
          >我已核查订单和账务记录，确认上述结论</el-checkbox
        ></el-form
      >
      <template #footer
        ><el-button @click="resolveOpen = false" :disabled="resolving"
          >取消</el-button
        ><el-button
          type="danger"
          :loading="resolving"
          :disabled="
            !resolution.upstreamChecked ||
            resolution.evidence.trim().length < 10 ||
            (isLeidianService(resolveOrder) && !!leidianResolutionError(resolveQuote?.action, resolution)) ||
            (isJingyuService(resolveOrder) && !!jingyuResolutionError(resolveQuote?.action, resolution, resolveQuote?.quantity))
          "
          @click="resolve"
          >确认并记入审计</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>
<script setup>
import { computed, ref, shallowRef, watch, onActivated, onDeactivated, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  listServiceOrders,
  syncServiceOrder,
  getServiceOrderEvents,
  previewServiceAction,
  getServiceOperation,
  getAdminServiceOperation,
  resolveServiceOperation,
  getServiceRunLogs,
  getServiceOrderOptions,
  getServiceOrderAudit,
  previewServiceRefundSettlement,
} from "@/api/serviceCommerce";
import {
  serviceNames,
  stateName,
  orderStateName,
  serviceActionName,
  moneyText,
  fieldNames,
  editableWuxinPlan,
  maxRefundableUnits,
} from "@/utils/serviceCommerce";
import InternshipPlanFields from "@/components/InternshipPlanFields.vue";
import ServiceNotifications from "@/components/ServiceNotifications.vue";
import ServiceStatusCheck from "@/components/ServiceStatusCheck.vue";
import ServiceOrderFilters from "@/components/ServiceOrderFilters.vue";
import { useServiceOrderSearch } from "@/composables/useServiceOrderSearch";
import { authSessionScope, hasAuthenticatedSession } from "@/utils/authSession";
import TotalDistancePlanSummary from "@/components/TotalDistancePlanSummary.vue";
import { isTotalDistanceService } from "@/utils/totalDistanceServices";
const notificationOpen = ref(false), notificationOrder = ref(null);
import {
  internshipFields,
  internshipReportTypes,
  internshipCalendarText,
  eligibleInternshipDates,
  internshipReportRangeError,
  pickerCalendarDate,
} from "@/utils/internshipServices";
import WuxinPlanFields from "@/components/WuxinPlanFields.vue";
import AppuiPlanFields from "@/components/AppuiPlanFields.vue";
import LeidianPlanFields from "@/components/LeidianPlanFields.vue";
import ServiceScoreInfo from "@/components/ServiceScoreInfo.vue";
import LeidianTaskTimeEditor from "@/components/LeidianTaskTimeEditor.vue";
import JingyuTaskTimeEditor from "@/components/JingyuTaskTimeEditor.vue";
import { isJingyuService, jingyuLogsValid, jingyuTaskEditable, jingyuResolutionError, jingyuResolutionPayload } from "@/utils/jingyuServices";
import { isLeidianService, canReadLeidianRecord, editableLeidianPlan, leidianPlanError,
  leidianLogsValid, leidianResolutionError, leidianResolutionPayload } from "@/utils/leidianServices";
import { latestRequest } from "@/utils/pluginIntegrations";
import { serviceDestination, serviceTypeFromQuery } from "@/utils/pluginWorkflows";
import { appuiPlanError, editableAppuiPlan, appuiRemainingText } from "@/utils/appuiServices";
import ServiceQuoteConfirm from "@/components/ServiceQuoteConfirm.vue";
const route = useRoute(),
  router = useRouter();
// MainLayout keys cached views by path. Keep each instance's role and query
// separate so another route cannot trigger reads or erase its saved filters.
const viewPath = route.path;
const admin = ref(!!route.meta.serviceAdmin);
const viewQuery = shallowRef(route.query);
const sessionActive = hasAuthenticatedSession;
const viewActive = computed(() => sessionActive.value && route.path === viewPath);
watch(() => route.path === viewPath ? route.query : null, (query) => {
  if (query) viewQuery.value = query;
}, { flush: "sync" });
const hasFocus = computed(() => Object.hasOwn(viewQuery.value, "focus"));
const { draft, applied, dirty, result, items, total, page, loading, error, validation,
  submit, refresh: load, goToPage, retry, reset, pause } = useServiceOrderSearch(listServiceOrders, {
  scope: () => JSON.stringify([admin.value, authSessionScope.value]),
  admin: () => admin.value,
  active: () => sessionActive.value,
  visible: () => viewActive.value,
  focus: () => hasFocus.value ? viewQuery.value.focus : "",
  preset: () => serviceTypeFromQuery(viewQuery.value.providerType),
});
onDeactivated(pause);
onActivated(() => {
  if (!result.value && !loading.value) void load();
});
const filterSummary = computed(() => {
  const f = applied.value, labels = [];
  if (f.keyword) labels.push(`搜索：${f.keyword}`);
  if (f.providerType) labels.push(serviceNames[f.providerType]);
  if (f.status) labels.push(f.status === "COMPLETED" && !["sxdk_tw", "appui"].includes(f.providerType)
    ? "已结束 / 已完成" : orderStateName({ providerType: f.providerType, status: f.status }));
  if (f.createdFrom || f.createdTo) labels.push(`创建日期：${f.createdFrom || "不限"} 至 ${f.createdTo || "不限"}`);
  if (f.ownerId) labels.push(`所属用户：${f.ownerId}`);
  return labels;
});
const emptyDescription = computed(() => hasFocus.value ? "未找到可查看的指定订单"
  : Object.keys(applied.value).length ? "没有符合筛选条件的订单，请调整条件后查询"
  : "还没有服务订单");
function clearFocus() {
  const { focus: _focus, ...query } = route.query;
  return router.replace({ path: route.path, query, hash: route.hash });
}
const internshipOpen = ref(false),
  internshipOrder = ref(null),
  internshipForm = ref({}),
  internshipSchedule = ref(null),
  internshipLoading = ref(false);
const reportOpen = ref(false),
  reportOrder = ref(null),
  reportDates = ref([]),
  reportForm = ref({ startDate: "", endDate: "", reportType: "日报" });
const busyId = ref(null),
  quote = ref(null);
const events = ref([]),
  eventsOrder = ref(null),
  eventsOpen = ref(false),
  resolveOpen = ref(false),
  resolveQuote = ref(null),
  resolveOrder = ref(null),
  resolving = ref(false);
const runLogsOpen = ref(false),
  runLogs = ref({ items: [], hasMore: false }),
  runLogOrder = ref(null),
  runLogPage = ref(1),
  runLogsLoading = ref(false), runLogsError = ref("");
const logRequests = latestRequest();
let detailVersion = 0, logContextVersion = 0;
const scoreOpen = ref(false), scoreOrder = ref(null), taskTimeOpen = ref(false), timeTask = ref(null);
const planOpen = ref(false),
  planOptions = ref(null),
  planFields = ref({}),
  planOrder = ref(null),
  planLoading = ref(false);
const audit = ref(null),
  auditOpen = ref(false),
  settlementOpen = ref(false),
  settlementOrder = ref(null),
  settlementAudit = ref(null),
  settlementLoading = ref(false);
const settlement = ref({
  refundedUnits: 0,
  evidence: "",
  upstreamChecked: false,
});
const resolution = ref({
  outcome: "NOT_ACCEPTED",
  externalOrderNo: "",
  externalSubOrderNo: "",
  refundedUnits: 0,
  evidence: "",
  upstreamChecked: false,
});
const jingyuSettlementError = computed(() => isJingyuService(settlementOrder.value)
  ? jingyuResolutionError("REFUND", { ...settlement.value, outcome: "ACCEPTED" }, maxRefundableUnits(settlementOrder.value)) : "");
function tagType(state, providerType) {
  if (providerType === "sxdk_tw" && state === "COMPLETED") return "info";
  return ["COMPLETED", "REFUNDED"].includes(state)
    ? "success"
    : ["CONFIRMING", "ATTENTION", "REFUND_REVIEW", "SUBMISSION_REVIEW"].includes(state)
      ? "warning"
      : "info";
}
function onQuoteResult(result) {
  if (result.state === "SUCCEEDED") {
    // A definitive receipt supersedes a previous lost-response error toast.
    ElMessage.closeAll();
    ElMessage.success(
      result.action === "SETTLE_REFUND"
        ? "退款已入账，可在审计记录中查看。"
        : result.action === "CANCEL" ? "订单取消已受理，退款尚未入账，请联系管理员核对。"
        : `${serviceActionName(result.action, result)}已受理。`,
    );
  }
  if (result.action === "CANCEL") { runLogsOpen.value = false; scoreOpen.value = false; taskTimeOpen.value = false; }
  load();
}
async function sync(item) {
  if (busyId.value || (isLeidianService(item) && !canReadLeidianRecord(item))) return;
  busyId.value = item.id;
  try {
    await syncServiceOrder(item.id);
  } catch {
  } finally {
    // Fetch saved local state after either outcome; never retry the remote read automatically.
    await load();
    busyId.value = null;
  }
}
async function checkPending(item) {
  busyId.value = item.id;
  try {
    const op = await getServiceOperation(item.pendingOperationId);
    ElMessage({
      type: op.state === "SUCCEEDED" ? "success" : "warning",
      message: stateName(op.state),
    });
    await load();
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function showEvents(item) {
  const current = detailVersion;
  try {
    const result = await getServiceOrderEvents(item.id);
    if (current !== detailVersion || !sessionActive.value) return;
    events.value = result;
    eventsOrder.value = item;
    eventsOpen.value = true;
  } catch {}
}
async function prepareAction(item, action) {
  if (busyId.value || !sessionActive.value || (isLeidianService(item) && !canReadLeidianRecord(item))) return;
  if (isJingyuService(item) && (item.pendingOperationId || !["ACTIVE", "PAUSED", "ATTENTION"].includes(item.status) ||
      !item.actions?.includes(action))) return;
  const current = detailVersion;
  if (action === "SCORE_INFO") {
    if (canReadLeidianRecord(item)) { scoreOrder.value = item; scoreOpen.value = true; }
    return;
  }
  if (action === "EDIT_SCHEDULE") {
    await editInternship(item);
    return;
  }
  if (action === "REPORT") {
    busyId.value = item.id;
    try {
      const options = await getServiceOrderOptions(item.id);
      if (current !== detailVersion || !sessionActive.value) return;
      reportOrder.value = item;
      reportDates.value = eligibleInternshipDates(options);
      if (!reportDates.value.length) {
        ElMessage.info("当前没有可补交的已购服务日");
        return;
      }
      reportForm.value = {
        startDate: "",
        endDate: "",
        reportType: internshipReportTypes(item.project)[0],
      };
      reportOpen.value = true;
    } catch {
    } finally {
      if (current === detailVersion) busyId.value = null;
    }
    return;
  }
  if (action === "EDIT_PLAN") {
    await editPlan(item);
    return;
  }
  let quantity = 0;
  if (action === "ADD_TIMES") {
    try {
      const value = await ElMessageBox.prompt(
        item.providerType === "appui" ? "输入增加的天数（1–365）" : "输入增加的次数（1–365）",
        serviceActionName("ADD_TIMES", item),
        {
          inputValue: "1",
          inputPattern: /^(?:[1-9]\d?|[12]\d{2}|3[0-5]\d|36[0-5])$/,
          inputErrorMessage: "请输入 1–365 的整数",
        },
      );
      quantity = Number(value.value);
    } catch {
      return;
    }
  }
  if (current !== detailVersion || !sessionActive.value) return;
  busyId.value = item.id;
  try {
    const result = await previewServiceAction(item.id, { action, quantity });
    if (current !== detailVersion || !sessionActive.value) return;
    quote.value = result;
  } catch {
  } finally {
    if (current === detailVersion) busyId.value = null;
  }
}
async function showRunLogs(item) {
  if (!sessionActive.value || (isLeidianService(item) && !canReadLeidianRecord(item))) return;
  if (isJingyuService(item) && item.pendingOperationId) return;
  logContextVersion++; taskTimeOpen.value = false;
  logRequests.invalidate(); runLogsLoading.value = false;
  runLogOrder.value = item; runLogPage.value = 1; runLogsOpen.value = true;
  await changeLogPage(0);
}
async function changeLogPage(delta) {
  if (runLogsLoading.value || !runLogOrder.value || !runLogsOpen.value || !sessionActive.value || ![-1, 0, 1].includes(delta)) return;
  const item = runLogOrder.value, next = runLogPage.value + delta;
  if (next < 1 || (isLeidianService(item) && (next > 100 || !canReadLeidianRecord(item)))) return;
  if (isJingyuService(item) && (next > 19 || item.pendingOperationId)) return;
  logContextVersion++; taskTimeOpen.value = false;
  const request = logRequests.begin();
  runLogPage.value = next; runLogsLoading.value = true; runLogsError.value = "";
  runLogs.value = { items: [], hasMore: false };
  try {
    const result = await getServiceRunLogs(item.id, next, request.signal);
    if (!request.current()) return;
    if (isLeidianService(item) && !leidianLogsValid(result, next)) throw new Error("Invalid run log page");
    if (isJingyuService(item) && !jingyuLogsValid(result, next)) throw new Error("Invalid run log page");
    runLogs.value = result;
  } catch {
    if (request.current()) runLogsError.value = "暂时无法读取这一页记录，请重试。";
  } finally {
    if (request.current()) runLogsLoading.value = false;
  }
}
function taskTimeQuote(value) {
  if (!sessionActive.value || !taskTimeOpen.value || !runLogsOpen.value || timeTask.value?.orderId !== runLogOrder.value?.id) return;
  quote.value = value; runLogsOpen.value = false;
}
async function delayTask(log) {
  const item = runLogOrder.value;
  if (busyId.value || !item || !sessionActive.value || !runLogsOpen.value || runLogsLoading.value) return;
  if (isJingyuService(item) && !jingyuTaskEditable(item, runLogs.value, log, "DELAY_TASK")) return;
  const current = detailVersion, logVersion = logContextVersion, page = runLogPage.value;
  busyId.value = item.id;
  try {
    const result = await previewServiceAction(item.id, {
      action: "DELAY_TASK", quantity: 0, fields: { taskId: log.id, page: String(page) },
    });
    if (current !== detailVersion || logVersion !== logContextVersion || !sessionActive.value || !runLogsOpen.value || runLogOrder.value !== item) return;
    quote.value = result; runLogsOpen.value = false;
  } catch {
  } finally {
    if (current === detailVersion && busyId.value === item.id) busyId.value = null;
  }
}
async function editTaskTime(log) {
  if (!sessionActive.value || !runLogsOpen.value || runLogsLoading.value || busyId.value) return;
  if (isJingyuService(runLogOrder.value)) {
    if (!jingyuTaskEditable(runLogOrder.value, runLogs.value, log, "CHANGE_TIME")) return;
    timeTask.value = Object.freeze({ providerType: "jingyu", orderId: runLogOrder.value.id, id: log.id, time: log.time, page: runLogPage.value });
    taskTimeOpen.value = true;
    return;
  }
  if (isLeidianService(runLogOrder.value)) {
    if (runLogsLoading.value || !canReadLeidianRecord(runLogOrder.value) || log.editable !== true ||
        !runLogOrder.value.actions.includes("CHANGE_TIME") || !runLogs.value.items.includes(log)) return;
    timeTask.value = Object.freeze({ orderId: runLogOrder.value.id, id: log.id, time: log.time, page: runLogPage.value });
    taskTimeOpen.value = true;
    return;
  }
  try {
    const result = await ElMessageBox.prompt(
      "填写未来的北京时间，格式 YYYY-MM-DD HH:mm:ss",
      "修改任务时间",
      {
        inputValue: log.time,
        inputPattern: /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/,
        inputErrorMessage: "请按要求填写日期和时间",
      },
    );
    quote.value = await previewServiceAction(runLogOrder.value.id, {
      action: "CHANGE_TIME",
      quantity: 0,
      fields: {
        taskId: log.id,
        time: result.value,
        page: String(runLogPage.value),
      },
    });
  } catch {}
}
async function editInternship(item) {
  if (internshipLoading.value) return;
  internshipLoading.value = true;
  try {
    const options = await getServiceOrderOptions(item.id);
    internshipOrder.value = item;
    internshipForm.value = {
      ...internshipFields(options.suggested, true),
      password: "",
    };
    internshipSchedule.value = options.schedule;
    internshipOpen.value = true;
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
async function previewInternship() {
  if (internshipLoading.value) return;
  internshipLoading.value = true;
  try {
    quote.value = await previewServiceAction(internshipOrder.value.id, {
      action: "EDIT_SCHEDULE",
      quantity: 0,
      fields: internshipFields(internshipForm.value, true),
      schedule: internshipSchedule.value,
    });
    internshipOpen.value = false;
    internshipForm.value.password = "";
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
function reportDateDisabled(date) {
  return !reportDates.value.includes(pickerCalendarDate(date));
}
async function previewReport() {
  if (internshipLoading.value) return;
  const message = internshipReportRangeError(
    reportForm.value,
    reportDates.value,
  );
  if (message) {
    ElMessage.warning(message);
    return;
  }
  internshipLoading.value = true;
  try {
    quote.value = await previewServiceAction(reportOrder.value.id, {
      action: "REPORT",
      quantity: 0,
      fields: reportForm.value,
    });
    reportOpen.value = false;
  } catch {
  } finally {
    internshipLoading.value = false;
  }
}
async function editPlan(item) {
  if (busyId.value || (isLeidianService(item) && !canReadLeidianRecord(item))) return;
  const current = ++detailVersion;
  busyId.value = item.id;
  planOptions.value = null;
  planFields.value = {};
  try {
    const options = await getServiceOrderOptions(item.id);
    if (current !== detailVersion || !sessionActive.value) return;
    planOptions.value = options;
    planOrder.value = item;
    planFields.value = isLeidianService(item) ? editableLeidianPlan(planOptions.value.suggested) : item.providerType === "appui"
      ? editableAppuiPlan(planOptions.value.suggested)
      : editableWuxinPlan(planOptions.value.suggested, item.distance);
    planOpen.value = true;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function previewPlan() {
  if (planLoading.value || !planOrder.value ||
      (planOrder.value.providerType === "appui" && appuiPlanError(planFields.value))) return;
  if (isLeidianService(planOrder.value)) {
    const error = leidianPlanError(planFields.value);
    if (error) { ElMessage.warning(error); return; }
  }
  const current = detailVersion;
  planLoading.value = true;
  try {
    const result = await previewServiceAction(planOrder.value.id, {
      action: "EDIT_PLAN",
      quantity: 0,
      fields: isLeidianService(planOrder.value) ? editableLeidianPlan(planFields.value) : planFields.value,
    });
    if (current !== detailVersion || !planOpen.value || !sessionActive.value) return;
    quote.value = result;
    planOpen.value = false;
  } catch {
  } finally {
    planLoading.value = false;
  }
}
async function showAudit(item) {
  if (busyId.value || !sessionActive.value) return;
  const current = ++detailVersion;
  busyId.value = item.id;
  try {
    const result = await getServiceOrderAudit(item.id);
    if (current !== detailVersion || !sessionActive.value) return;
    audit.value = result;
    auditOpen.value = true;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function openSettlement(item) {
  if (settlementLoading.value || busyId.value || !sessionActive.value) return;
  const current = ++detailVersion;
  settlementLoading.value = true;
  busyId.value = item.id;
  try {
    const fresh = await getServiceOrderAudit(item.id);
    if (current !== detailVersion || !sessionActive.value) return;
    if (
      fresh.order.status !== "REFUND_REVIEW" ||
      fresh.order.pendingOperationId
    ) {
      await load();
      return;
    }
    settlementAudit.value = fresh;
    settlementOrder.value = fresh.order;
    settlement.value = {
      refundedUnits: isJingyuService(fresh.order) ? null :
        fresh.order.completed == null ? 0 : maxRefundableUnits(fresh.order),
      evidence: "",
      upstreamChecked: false,
    };
    settlementOpen.value = true;
  } catch {
  } finally {
    settlementLoading.value = false;
    busyId.value = null;
  }
}
async function previewSettlement() {
  if (settlementLoading.value || !settlementOpen.value || !settlementOrder.value || !sessionActive.value) return;
  if (jingyuSettlementError.value) { ElMessage.warning(jingyuSettlementError.value); return; }
  const current = detailVersion;
  settlementLoading.value = true;
  try {
    const result = await previewServiceRefundSettlement(
      settlementOrder.value.id,
      { ...settlement.value, orderVersion: settlementOrder.value.version },
    );
    if (current !== detailVersion || !sessionActive.value) return;
    if (!settlementOpen.value) return;
    quote.value = result;
    settlementOpen.value = false;
  } catch {
  } finally {
    settlementLoading.value = false;
  }
}
async function openResolve(item) {
  if (busyId.value || !sessionActive.value) return;
  const current = ++detailVersion;
  busyId.value = item.id;
  try {
    const operation = await getAdminServiceOperation(item.pendingOperationId);
    if (current !== detailVersion || !sessionActive.value) return;
    resolveQuote.value = operation;
    resolveOrder.value = item;
    resolution.value = {
      outcome: "NOT_ACCEPTED",
      externalOrderNo: "",
      externalSubOrderNo: "",
      refundedUnits: isTotalDistanceService(item) || isLeidianService(item) || isJingyuService(item) ? null : 0,
      evidence: "",
      upstreamChecked: false,
    };
    resolveOpen.value = true;
  } catch {
  } finally {
    busyId.value = null;
  }
}
async function resolve() {
  if (resolving.value || !resolveQuote.value || !resolveOrder.value || !sessionActive.value) return;
  if (isJingyuService(resolveOrder.value)) {
    const error = jingyuResolutionError(resolveQuote.value.action, resolution.value, resolveQuote.value.quantity);
    if (error) { ElMessage.warning(error); return; }
  }
  if (isLeidianService(resolveOrder.value)) {
    const error = leidianResolutionError(resolveQuote.value.action, resolution.value);
    if (error) { ElMessage.warning(error); return; }
  }
  const current = detailVersion;
  resolving.value = true;
  try {
    const body = isJingyuService(resolveOrder.value) ? jingyuResolutionPayload(resolveQuote.value.action, resolution.value) : isLeidianService(resolveOrder.value)
      ? leidianResolutionPayload(resolveQuote.value.action, resolution.value) : resolution.value;
    await resolveServiceOperation(resolveQuote.value.id, body);
    if (current !== detailVersion || !sessionActive.value) return;
    resolveOpen.value = false;
    ElMessage.success("核对结果已记录");
    await load();
  } catch {
  } finally {
    resolving.value = false;
  }
}
watch(internshipOpen, (open) => {
  if (!open) internshipForm.value.password = "";
});
watch(planOpen, (open) => {
  if (!open && ["appui", "leidian"].includes(planOrder.value?.providerType)) planFields.value = {};
});
watch(runLogsOpen, (open) => {
  if (!open) { logContextVersion++; taskTimeOpen.value = false; logRequests.invalidate(); runLogs.value = { items: [], hasMore: false }; runLogsLoading.value = false; runLogsError.value = ""; }
}, { flush: "sync" });
watch(scoreOpen, (open) => { if (!open) scoreOrder.value = null; });
watch(taskTimeOpen, (open) => { if (!open) timeTask.value = null; });
watch(resolveOpen, (open) => {
  if (!open) { resolveOrder.value = null; resolveQuote.value = null; resolution.value = { outcome: "NOT_ACCEPTED", evidence: "", upstreamChecked: false }; }
});
function clearPrivateDetails() {
  detailVersion++; logContextVersion++; logRequests.invalidate();
  runLogsOpen.value = scoreOpen.value = taskTimeOpen.value = planOpen.value = resolveOpen.value = false;
  eventsOpen.value = auditOpen.value = settlementOpen.value = notificationOpen.value = internshipOpen.value = reportOpen.value = false;
  scoreOrder.value = timeTask.value = planOrder.value = runLogOrder.value = resolveOrder.value = null;
  quote.value = audit.value = settlementAudit.value = settlementOrder.value = eventsOrder.value = null;
  events.value = []; runLogs.value = { items: [], hasMore: false }; runLogsLoading.value = false;
  planFields.value = {}; internshipForm.value = {}; resolution.value = { outcome: "NOT_ACCEPTED", evidence: "", upstreamChecked: false };
  busyId.value = null;
  planLoading.value = resolving.value = settlementLoading.value = false;
}
watch([admin, authSessionScope, viewActive], clearPrivateDetails, { flush: "sync" });
onBeforeUnmount(clearPrivateDetails);
</script>
<style scoped>
.leidian-order .el-button, .jingyu-order .el-button, :global(.service-settlement .el-button), :global(.service-settlement .el-input__wrapper), :global(.service-run-logs .el-button), :global(.service-resolution .el-button), :global(.service-resolution .el-input__wrapper) { min-height: 44px; }
:global(.service-resolution .el-radio), :global(.service-resolution .el-checkbox) { min-height: 44px; height: auto; }
:global(.service-resolution .el-radio-group) { display: flex; flex-wrap: wrap; gap: 8px; }
:global(.service-resolution .el-checkbox__label), :global(.service-resolution .el-radio__label) { white-space: normal; line-height: 1.6; }
.log-end-time { color: var(--el-text-color-regular); line-height: 1.7; font-size: 13px; }
:global(.service-plan-editor) { display: flex; flex-direction: column; max-height: calc(100dvh - 48px); margin: 24px auto !important; }
:global(.service-plan-editor .el-dialog__body) { overflow: auto; min-height: 0; flex: 1; }
:global(.service-plan-editor .el-button) { min-height: 44px; }
:global(.internship-editor) {
  display: flex;
  flex-direction: column;
  max-height: calc(100dvh - 48px);
  margin: 24px auto !important;
}
:global(.internship-editor .el-dialog__body) {
  overflow: auto;
  min-height: 0;
  flex: 1;
}

.service-orders {
  max-width: 1160px;
  margin: auto;
}
.orders-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 28px;
}
.orders-header > div {
  flex: 1;
}
.orders-header h1 {
  font-size: 28px;
  margin: 8px 0;
}
.orders-header p {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.8;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 1.6px;
  color: var(--el-color-primary);
  font-weight: 700;
}
.order-focus { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 16px 20px; margin-bottom: 16px; border: 1px solid var(--el-color-primary-light-7); border-radius: 12px; background: var(--el-color-primary-light-9); }
.order-focus p { margin: 6px 0 0; font-size: 13px; line-height: 1.7; color: var(--el-text-color-secondary); }
.order-focus .el-button { min-height: 44px; flex-shrink: 0; }
.applied-filters { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; font-size: 13px; }
.applied-filters .el-tag { height: auto; min-height: 26px; white-space: normal; overflow-wrap: anywhere; max-width: 100%; line-height: 1.6; }
.search-status { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; margin-bottom: 14px; font-size: 13px; color: var(--el-text-color-secondary); }
.el-alert { margin-bottom: 16px; }
.el-alert .el-button { min-height: 44px; margin-top: 10px; }
@media (max-width: 600px) {
  .order-focus { align-items: stretch; flex-direction: column; }
}
.order-list {
  display: grid;
  gap: 18px;
  min-height: 100px;
}
.order-card {
  padding: 24px;
  border: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  border-radius: 18px;
}
.order-card.focused {
  border-color: var(--el-color-primary);
}
.order-title {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
.order-title small {
  color: var(--el-text-color-secondary);
  font-size: 11px;
}
.order-title h2 {
  font-size: 18px;
  margin: 8px 0 0;
}
.order-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 24px 0;
}
.order-metrics span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.order-metrics strong {
  font-size: 19px;
}
.order-metrics small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  font-weight: 400;
}
.order-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 22px;
}
.order-number {
  font: 11px monospace;
  overflow-wrap: anywhere;
  color: var(--el-text-color-secondary);
}
.order-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.order-actions .el-button + .el-button {
  margin-left: 0;
}
.order-card .el-alert {
  margin-top: 18px;
}
.unknown-progress {
  font-size: 12px;
  line-height: 1.8;
  color: var(--el-text-color-secondary);
  margin-top: 16px;
}
.resolve-form {
  margin-top: 18px;
}
.audit-evidence {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
}
.resolve-form :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.7;
}
@media (max-width: 600px) {
  .orders-header {
    flex-wrap: wrap;
  }
  .orders-header > div {
    flex-basis: 100%;
  }
  .orders-header h1 {
    font-size: 24px;
  }
  .order-card {
    padding: 18px;
  }
  .order-metrics {
    grid-template-columns: 1fr 1fr;
    gap: 18px;
  }
  .order-actions {
    width: 100%;
  }
  .order-actions .el-button {
    min-height: 44px;
  }
}
</style>
