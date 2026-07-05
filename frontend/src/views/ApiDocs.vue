<template>
  <div class="api-docs-page">
    <el-card class="header-card">
      <template #header>
        <div class="card-header">
          <span class="title">🔌 第三方API对接文档</span>
          <el-tag type="info">实时测试工具</el-tag>
        </div>
      </template>

      <el-alert title="使用说明" type="info" :closable="false" show-icon>
        <template #default>
          <div class="usage-info">
            <p>本页面提供完整的第三方API对接文档和在线测试工具</p>
            <p>
              所有接口均需使用 <code>uid</code> 和 <code>api_key</code> 进行认证
            </p>
            <!-- 基础url：frp-dad.com:14255 -->
            <p>
              基础请求URL：<code>{{ apiBaseUrl }}</code>
            </p>
            <p>点击接口卡片可展开查看详细说明和在线测试</p>
          </div>
        </template>
      </el-alert>

      <!-- API密钥管理 -->
      <div class="api-key-section">
        <el-form :inline="true" :model="credentials">
          <el-form-item label="用户ID (UID)">
            <el-input
              v-model="credentials.uid"
              placeholder="请输入用户ID"
              style="width: 200px"
            />
          </el-form-item>
          <el-form-item label="API密钥 (Key)">
            <el-input
              v-model="credentials.key"
              placeholder="请输入API密钥"
              style="width: 300px"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="saveCredentials"
              >保存凭证</el-button
            >
            <el-button
              type="primary"
              icon="el-icon-key"
              @click="generateApiKey"
              plain
              title="开通API密钥"
              >开通API密钥</el-button
            >
          </el-form-item>
        </el-form>
      </div>
    </el-card>

    <!-- API接口列表 -->
    <div class="api-list">
      <!-- 1. 查询余额 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('getMoney')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">查询余额</span>
              <span class="api-path">/api/external/getmoney</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('getMoney') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div v-show="expandedApis.includes('getMoney')" class="api-content">
            <div class="api-description">
              <h4>📝 接口说明</h4>
              <p>查询指定用户的账户余额</p>
            </div>

            <div class="api-params">
              <h4>📋 请求参数</h4>
              <el-table :data="getMoneyParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-button
                type="primary"
                @click="testGetMoney"
                :loading="loading.getMoney"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.getMoney" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.getMoney) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/getmoney' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/getmoney', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/getmoney', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台查询余额对接示例
// 文件位置: 您的系统任意位置
// 平台标识: "YourPlatform" 
// ========================================

// 方式一：独立调用
$api_url = "{{ apiBaseUrl }}/api/external/getmoney";
$data = array(
    "uid" => "YOUR_UID",      // 二开台为您分配的用户ID
    "key" => "YOUR_API_KEY"   // 二开台为您分配的API密钥
);

$result = get_url($api_url, $data);
$result = json_decode($result, true);

if ($result["code"] == "1") {
    $balance = $result["data"]["balance"];
    echo "当前余额: ¥" . $balance;
} else {
    echo "查询失败: " . $result["msg"];
}

// 方式二：集成到您的平台函数中
// 假设 $a["user"] 和 $a["pass"] 是您配置的UID和KEY
$api_rl = $a["url"];  // 二开台API地址
$api_url = "$api_rl/api/external/getmoney";
$data = array(
    "uid" => $a["user"],
    "key" => $a["pass"]
);
$result = get_url($api_url, $data);
$result = json_decode($result, true);
return $result;</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 2. 获取平台列表 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('getPlatforms')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">获取平台列表</span>
              <span class="api-path">/api/external/get-platforms</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('getPlatforms') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div
            v-show="expandedApis.includes('getPlatforms')"
            class="api-content"
          >
            <div class="api-description">
              <h4>接口说明</h4>
              <p>获取所有可用的课程平台列表，用于下单前选择平台</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="getPlatformsParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>🧪 在线测试</h4>
              <el-button
                type="primary"
                @click="testGetPlatforms"
                :loading="loading.getPlatforms"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.getPlatforms" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.getPlatforms) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/get-platforms' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/get-platforms', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/get-platforms', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台获取平台列表对接示例
// 文件位置: 您的系统任意位置
// 用途: 获取可用的课程平台列表供用户选择
// ========================================

$api_url = "{{ apiBaseUrl }}/api/external/get-platforms";
$data = array(
    "uid" => "YOUR_UID",      // 二开台用户ID
    "key" => "YOUR_API_KEY"   // 二开台API密钥
);

$result = get_url($api_url, $data);
$result = json_decode($result, true);

if ($result["code"] == "1") {
    foreach ($result["data"] as $platform) {
        echo $platform["id"] . ": " . $platform["name"] . "\n";
    }
} else {
    echo "获取失败: " . $result["msg"];
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 3.查课 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('queryCourses')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">查课</span>
              <span class="api-path">/api/external/query-courses</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('queryCourses') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div
            v-show="expandedApis.includes('queryCourses')"
            class="api-content"
          >
            <div class="api-description">
              <h4>接口说明</h4>
              <p>根据学生账号密码查询该学生的课程列表</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="queryCoursesParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-form :inline="true" :model="testForms.queryCourses">
                <el-form-item label="平台ID">
                  <el-input
                    v-model="testForms.queryCourses.platform"
                    placeholder="平台ID"
                    style="width: 120px"
                  />
                </el-form-item>
                <el-form-item label="学校名称">
                  <el-input
                    v-model="testForms.queryCourses.school"
                    placeholder="可选"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="学生账号">
                  <el-input
                    v-model="testForms.queryCourses.user"
                    placeholder="学生账号"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="学生密码">
                  <el-input
                    v-model="testForms.queryCourses.pass"
                    placeholder="学生密码"
                    type="password"
                    style="width: 150px"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="primary"
                @click="testQueryCourses"
                :loading="loading.queryCourses"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.queryCourses" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.queryCourses) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/query-courses' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}&platform=PLATFORM_ID&user=STUDENT_USER&pass=STUDENT_PASS'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/query-courses', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}',
    platform: 'PLATFORM_ID',
    school: 'SCHOOL_NAME',  // 可选
    user: 'STUDENT_USER',
    pass: 'STUDENT_PASS'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/query-courses', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}',
    'platform': 'PLATFORM_ID',
    'school': 'SCHOOL_NAME',  # 可选
    'user': 'STUDENT_USER',
    'pass': 'STUDENT_PASS'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台查课接口对接示例
// 文件位置: /Checkorder/ckjk.php 的 getWk() 函数中
// 平台标识: "erk"
// ========================================

else if ($type == "erk")
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "school" => $school,      // 学校名称
        "user" => $user,          // 学生账号
        "pass" => $pass,          // 学生密码
        "platform" => $noun,      // 平台ID
        "kcid" => $kcid           // 课程ID(可选)
    );
    $erk_rl = $a["url"];         // 二开台的API地址
    $erk_url = "$erk_rl/api/external/query-courses";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    return $result;
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 4. 下单 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('addOrder')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">下单</span>
              <span class="api-path">/api/external/add</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('addOrder') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div v-show="expandedApis.includes('addOrder')" class="api-content">
            <div class="api-description">
              <h4>接口说明</h4>
              <p>创建新的课程订单</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="addOrderParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-form :inline="true" :model="testForms.addOrder">
                <el-form-item label="平台ID">
                  <el-input
                    v-model="testForms.addOrder.platform"
                    placeholder="平台ID"
                    style="width: 120px"
                  />
                </el-form-item>
                <el-form-item label="学校名称">
                  <el-input
                    v-model="testForms.addOrder.school"
                    placeholder="可选"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="学生账号">
                  <el-input
                    v-model="testForms.addOrder.user"
                    placeholder="学生账号"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="学生密码">
                  <el-input
                    v-model="testForms.addOrder.pass"
                    placeholder="学生密码"
                    type="password"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="课程ID">
                  <el-input
                    v-model="testForms.addOrder.kcid"
                    placeholder="可选"
                    style="width: 150px"
                  />
                </el-form-item>
                <el-form-item label="课程名称">
                  <el-input
                    v-model="testForms.addOrder.kcname"
                    placeholder="课程名称"
                    style="width: 200px"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="primary"
                @click="testAddOrder"
                :loading="loading.addOrder"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.addOrder" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.addOrder) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/add' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}&platform=PLATFORM_ID&user=STUDENT_USER&pass=STUDENT_PASS&kcname=COURSE_NAME'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/add', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}',
    platform: 'PLATFORM_ID',
    school: 'SCHOOL_NAME',  // 可选
    user: 'STUDENT_USER',
    pass: 'STUDENT_PASS',
    kcid: 'COURSE_ID',      // 可选
    kcname: 'COURSE_NAME'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/add', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}',
    'platform': 'PLATFORM_ID',
    'school': 'SCHOOL_NAME',  # 可选
    'user': 'STUDENT_USER',
    'pass': 'STUDENT_PASS',
    'kcid': 'COURSE_ID',      # 可选
    'kcname': 'COURSE_NAME'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台下单接口对接示例
// 文件位置: /Checkorder/xdjk.php 的 addWk() 函数中
// 平台标识: "erk" 
// ========================================

else if ($type == "erk") 
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "platform" => $noun,      // 平台ID
        "school" => $school,      // 学校名称
        "user" => $user,          // 学生账号
        "pass" => $pass,          // 学生密码
        "kcname" => $kcname,      // 课程名称
        "kcid" => $kcid           // 课程ID(可选)
    );
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/add";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    if ($result["code"] == "1") {
        $b = array("code" => 1, "msg" => "下单成功", "yid" => $result["data"]["orderNo"]);
    } else {
        $b = array("code" => -1, "msg" => $result["msg"]);
    }
    return $b;
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 5. 查单 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('chadan')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">查单</span>
              <span class="api-path">/api/external/chadan</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('chadan') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div v-show="expandedApis.includes('chadan')" class="api-content">
            <div class="api-description">
              <h4>接口说明</h4>
              <p>根据学生账号查询订单列表</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="chadanParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-form :inline="true" :model="testForms.chadan">
                <el-form-item label="学生账号">
                  <el-input
                    v-model="testForms.chadan.username"
                    placeholder="学生账号"
                    style="width: 200px"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="primary"
                @click="testChadan"
                :loading="loading.chadan"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.chadan" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.chadan) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/chadan' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}&username=STUDENT_USER'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/chadan', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}',
    username: 'STUDENT_USER'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/chadan', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}',
    'username': 'STUDENT_USER'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台查单接口对接示例
// 文件位置: 您的系统任意位置
// 用途: 根据学生账号查询该学生的所有订单
// ========================================

$api_url = "{{ apiBaseUrl }}/api/external/chadan";
$data = array(
    "uid" => "YOUR_UID",      // 二开台用户ID
    "key" => "YOUR_API_KEY",  // 二开台API密钥
    "username" => $username   // 学生账号
);

$result = get_url($api_url, $data);
$result = json_decode($result, true);

if ($result["code"] == "1") {
    foreach ($result["data"] as $order) {
        echo "订单号: " . $order["orderNo"] . "\n";
        echo "课程名: " . $order["courseName"] . "\n";
        echo "状态: " . $order["statusText"] . "\n";
        echo "进度: " . $order["progress"] . "\n";
        echo "----------------------------\n";
    }
} else {
    echo "查询失败: " . $result["msg"];
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 6. 查询/同步订单进度 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('queryProgress')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">查询/同步订单进度</span>
              <span class="api-path">/api/external/query-progress</span>
            </div>
            <el-icon
              :class="{ 'rotate-icon': expandedApis.includes('queryProgress') }"
            >
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div
            v-show="expandedApis.includes('queryProgress')"
            class="api-content"
          >
            <div class="api-description">
              <h4>接口说明</h4>
              <p>根据订单编号查询订单的详细进度信息</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="queryProgressParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-form :inline="true" :model="testForms.queryProgress">
                <el-form-item label="订单编号">
                  <el-input
                    v-model="testForms.queryProgress.orderNo"
                    placeholder="订单编号 (ORD...)"
                    style="width: 300px"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="primary"
                @click="testQueryProgress"
                :loading="loading.queryProgress"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.queryProgress" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.queryProgress) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/query-progress' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}&orderNo=ORD1992226273535578112'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/query-progress', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}',
    orderNo: 'ORD1992226273535578112'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/query-progress', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}',
    'orderNo': 'ORD1992226273535578112'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台查询订单进度接口对接示例
// 文件位置: /Checkorder/jdjk.php 的进度查询函数中
// 平台标识: "erk" 
// ========================================

else if ($type == "erk") {
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/query-progress?uid=".$a["user"].
                "&key=".$a["pass"].
                "&orderNo=".$orderNo;
    
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    
    if ($result["code"] == "1") {
        $b[] = array(
            "code" => 1,
            "msg" => "查询成功",
            "orderNo" => $result["data"]["orderNo"],
            "kcname" => $result["data"]["courseName"],
            "status" => $result["data"]["status"],
            "process" => $result["data"]["progress"],
            "remarks" => $result["data"]["statusText"]
        );
    } else {
        $b[] = array("code" => -1, "msg" => $result["msg"]);
    }
    return $b;
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>

      <!-- 7. 补单 -->
      <el-card class="api-card">
        <template #header>
          <div class="api-card-header" @click="toggleExpand('budan')">
            <div>
              <el-tag type="success" class="method-tag">POST</el-tag>
              <span class="api-title">补单</span>
              <span class="api-path">/api/external/budan</span>
            </div>
            <el-icon :class="{ 'rotate-icon': expandedApis.includes('budan') }">
              <ArrowRight />
            </el-icon>
          </div>
        </template>

        <el-collapse-transition>
          <div v-show="expandedApis.includes('budan')" class="api-content">
            <div class="api-description">
              <h4>接口说明</h4>
              <p>对失败的订单进行补单操作（最多5次）</p>
            </div>

            <div class="api-params">
              <h4>请求参数</h4>
              <el-table :data="budanParams" border size="small">
                <el-table-column prop="name" label="参数名" width="120" />
                <el-table-column prop="type" label="类型" width="80" />
                <el-table-column prop="required" label="必填" width="60">
                  <template #default="scope">
                    <el-tag
                      :type="scope.row.required ? 'danger' : 'info'"
                      size="small"
                    >
                      {{ scope.row.required ? "是" : "否" }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="description" label="说明" />
              </el-table>
            </div>

            <div class="api-test">
              <h4>在线测试</h4>
              <el-form :inline="true" :model="testForms.budan">
                <el-form-item label="订单编号">
                  <el-input
                    v-model="testForms.budan.orderNo"
                    placeholder="订单编号 (ORD...)"
                    style="width: 300px"
                  />
                </el-form-item>
              </el-form>
              <el-button
                type="primary"
                @click="testBudan"
                :loading="loading.budan"
              >
                <el-icon><VideoPlay /></el-icon>
                测试接口
              </el-button>
            </div>

            <div v-if="results.budan" class="api-result">
              <h4>响应结果</h4>
              <pre><code>{{ formatJson(results.budan) }}</code></pre>
            </div>

            <div class="api-examples">
              <h4>代码示例</h4>
              <el-tabs>
                <el-tab-pane label="cURL">
                  <pre><code>curl -X POST '{{ apiBaseUrl }}/api/external/budan' \
  -d 'uid={{ credentials.uid }}&key={{ credentials.key }}&orderNo=ORD1992226273535578112'</code></pre>
                </el-tab-pane>
                <el-tab-pane label="JavaScript">
                  <pre><code>const response = await fetch('{{ apiBaseUrl }}/api/external/budan', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/x-www-form-urlencoded',
  },
  body: new URLSearchParams({
    uid: '{{ credentials.uid }}',
    key: '{{ credentials.key }}',
    orderNo: 'ORD1992226273535578112'
  })
});
const data = await response.json();
console.log(data);</code></pre>
                </el-tab-pane>
                <el-tab-pane label="Python">
                  <pre><code>import requests

response = requests.post('{{ apiBaseUrl }}/api/external/budan', data={
    'uid': '{{ credentials.uid }}',
    'key': '{{ credentials.key }}',
    'orderNo': 'ORD1992226273535578112'
})
print(response.json())</code></pre>
                </el-tab-pane>
                <el-tab-pane label="29平台对接示例(PHP)">
                  <pre><code>// ========================================
// 29平台补单接口对接示例
// 文件位置: /Checkorder/bsjk.php 的 budanWk() 函数中
// 平台标识: "erk" 
// ========================================

elseif ($type == "erk") 
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "orderNo" => $yid         // 订单编号（注意：这里使用orderNo而非id）
    );
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/budan";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    return $result;
}</code></pre>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>
        </el-collapse-transition>
      </el-card>
    </div>

    <!-- 8. 对接平台代码示例 -->
    <el-card class="api-card">
      <template #header>
        <div
          class="api-card-header"
          @click="toggleExpand('platformIntegration')"
        >
          <div>
            <el-tag type="warning" class="method-tag">PHP</el-tag>
            <span class="api-title">29平台对接示例</span>
            <span class="api-path">/Checkorder/*.php</span>
          </div>
          <el-icon
            :class="{
              'rotate-icon': expandedApis.includes('platformIntegration'),
            }"
          >
            <ArrowRight />
          </el-icon>
        </div>
      </template>

      <el-collapse-transition>
        <div
          v-show="expandedApis.includes('platformIntegration')"
          class="api-content"
        >
          <div class="api-description">
            <h4>📝 对接说明</h4>
            <p>
              本文档适用于<strong>其他网课平台对接本二开台</strong>。以下提供完整的PHP对接代码模板，您只需：
            </p>
            <ul style="line-height: 1.8; margin: 10px 0">
              <li>
                1. 将平台标识 <code>"erk"</code> 替换为您的平台标识（如
                <code>"YourPlatform"</code>）
              </li>
              <li>2. 确保您的平台提供了对应的API接口</li>
              <li>3. 将以下代码添加到指定的PHP文件中</li>
            </ul>
          </div>

          <div class="api-examples">
            <h4>步骤1: 注册平台标识</h4>
            <p>
              文件路径：<code>/Checkorder/xdjk.php</code> 的
              <code>wkname()</code> 函数中
            </p>
            <pre><code>// 在 wkname() 函数的 $data 数组中添加：
"erk" => "erk",  // 格式: "标识符" => "显示名称"</code></pre>

            <h4>步骤2: 查课接口配置</h4>
            <p>
              文件路径：<code>/Checkorder/ckjk.php</code> 的
              <code>getWk()</code> 函数中
            </p>
            <pre><code>//erk平台查课接口 复制代码放在/Checkorder/ckjk.php 文件
else if ($type == "erk")
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "school" => $school,      // 学校名称
        "user" => $user,          // 学生账号
        "pass" => $pass,          // 学生密码
        "platform" => $noun,      // 平台ID
        "kcid" => $kcid           // 课程ID(可选)
    );
    $erk_rl = $a["url"];         // 二开台的API地址
    $erk_url = "$erk_rl/api/external/query-courses";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    return $result;
}</code></pre>

            <h4>步骤3: 下单接口配置</h4>
            <p>
              文件路径：<code>/Checkorder/xdjk.php</code> 的
              <code>addWk()</code> 函数中
            </p>
            <pre><code>//erk平台下单接口 复制代码放在/Checkorder/xdjk.php 文件
else if ($type == "erk") 
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "platform" => $noun,      // 平台ID
        "school" => $school,      // 学校名称
        "user" => $user,          // 学生账号
        "pass" => $pass,          // 学生密码
        "kcname" => $kcname,      // 课程名称
        "kcid" => $kcid           // 课程ID(可选)
    );
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/add";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    if ($result["code"] == "1") {
        $b = array("code" => 1, "msg" => "下单成功", "yid" => $result["data"]["orderNo"]);
    } else {
        $b = array("code" => -1, "msg" => $result["msg"]);
    }
    return $b;
}</code></pre>

            <h4>步骤4: 查询订单进度接口配置</h4>
            <p>文件路径：<code>/Checkorder/jdjk.php</code> 的进度查询函数中</p>
            <pre><code>//erk平台查询进度接口 复制代码放在/Checkorder/jdjk.php 文件
else if ($type == "erk") {
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/query-progress?uid=".$a["user"].
                "&key=".$a["pass"].
                "&orderNo=".$orderNo;
    
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    
    if ($result["code"] == "1") {
        $b[] = array(
            "code" => 1,
            "msg" => "查询成功",
            "orderNo" => $result["data"]["orderNo"],
            "kcname" => $result["data"]["courseName"],
            "status" => $result["data"]["status"],
            "process" => $result["data"]["progress"],
            "remarks" => $result["data"]["statusText"]
        );
    } else {
        $b[] = array("code" => -1, "msg" => $result["msg"]);
    }
    return $b;
}</code></pre>

            <h4>步骤5: 补单接口配置</h4>
            <p>
              文件路径：<code>/Checkorder/bsjk.php</code> 的
              <code>budanWk()</code> 函数中
            </p>
            <pre><code>//erk平台补刷接口 复制代码放在/Checkorder/bsjk.php 文件
elseif ($type == "erk") 
{
    $data = array(
        "uid" => $a["user"],      // 二开台为您分配的UID
        "key" => $a["pass"],      // 二开台为您分配的API密钥
        "orderNo" => $yid         // 订单编号（注意：这里使用orderNo而非id）
    );
    $erk_rl = $a["url"];
    $erk_url = "$erk_rl/api/external/budan";
    $result = get_url($erk_url, $data);
    $result = json_decode($result, true);
    return $result;
}</code></pre>

            <div
              style="
                margin-top: 20px;
                padding: 15px;
                background: #fff3cd;
                border-left: 4px solid #ffc107;
                border-radius: 4px;
              "
            >
              <h4 style="margin-top: 0; color: #856404">⚠️ 重要提示</h4>
              <ul style="margin: 10px 0; color: #856404; line-height: 1.8">
                <li>
                  <strong>参数映射</strong>：$a["user"] 和 $a["pass"]
                  是您在二开台后台配置的UID和API密钥
                </li>
                <li>
                  <strong>订单编号</strong>：补单接口使用 orderNo（字符串格式如
                  ORD19922...），而非数字ID
                </li>
                <li>
                  <strong>返回格式</strong
                  >：所有接口都返回统一的JSON格式，code=1表示成功，code=-1表示失败
                </li>
                <li>
                  <strong>API地址</strong>：基础URL为
                  <code>{{ apiBaseUrl }}</code>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </el-collapse-transition>
    </el-card>

    <!-- 错误码说明 -->
    <el-card class="error-codes-card">
      <template #header>
        <span class="title"> 错误码说明</span>
      </template>
      <el-table :data="errorCodes" border size="small">
        <el-table-column prop="code" label="错误码" width="100" />
        <el-table-column prop="message" label="说明" />
        <el-table-column prop="solution" label="解决方案" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from "vue";
import { ElMessage } from "element-plus";
import { ArrowRight, VideoPlay } from "@element-plus/icons-vue";
import * as externalApi from "@/api/external";
import router from "@/router";

const apiBaseUrl =
  import.meta.env.VITE_API_BASE_URL || "https://frp-dad.com:14255";

// 凭证信息
const credentials = reactive({
  uid: localStorage.getItem("api_test_uid") || "",
  key: localStorage.getItem("api_test_key") || "",
});

const generateApiKey = () => {
  router.push("/profile");
};

const saveCredentials = () => {
  localStorage.setItem("api_test_uid", credentials.uid);
  localStorage.setItem("api_test_key", credentials.key);
  ElMessage.success("凭证已保存");
};

// 展开的API列表
const expandedApis = ref([]);

const toggleExpand = (apiName) => {
  const index = expandedApis.value.indexOf(apiName);
  if (index > -1) {
    expandedApis.value.splice(index, 1);
  } else {
    expandedApis.value.push(apiName);
  }
};

// 加载状态
const loading = reactive({
  getMoney: false,
  getPlatforms: false,
  queryCourses: false,
  addOrder: false,
  chadan: false,
  queryProgress: false,
  budan: false,
});

// 测试结果
const results = reactive({
  getMoney: null,
  getPlatforms: null,
  queryCourses: null,
  addOrder: null,
  chadan: null,
  queryProgress: null,
  budan: null,
});

// 测试表单
const testForms = reactive({
  queryCourses: {
    platform: "",
    school: "",
    user: "",
    pass: "",
  },
  addOrder: {
    platform: "",
    school: "",
    user: "",
    pass: "",
    kcid: "",
    kcname: "",
  },
  chadan: {
    username: "",
  },
  queryProgress: {
    orderNo: "",
  },
  budan: {
    orderNo: "",
  },
});

// API参数定义
const getMoneyParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
];

const getPlatformsParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
];

const queryCoursesParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
  { name: "platform", type: "String", required: true, description: "平台ID" },
  { name: "school", type: "String", required: false, description: "学校名称" },
  { name: "user", type: "String", required: true, description: "学生账号" },
  { name: "pass", type: "String", required: true, description: "学生密码" },
];

const addOrderParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
  { name: "platform", type: "String", required: true, description: "平台ID" },
  { name: "school", type: "String", required: false, description: "学校名称" },
  { name: "user", type: "String", required: true, description: "学生账号" },
  { name: "pass", type: "String", required: true, description: "学生密码" },
  { name: "kcid", type: "String", required: false, description: "课程ID" },
  { name: "kcname", type: "String", required: true, description: "课程名称" },
];

const chadanParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
  { name: "username", type: "String", required: true, description: "学生账号" },
];

const queryProgressParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
  {
    name: "orderNo",
    type: "String",
    required: true,
    description: "订单编号 (如: ORD1992226273535578112)",
  },
];

const budanParams = [
  { name: "uid", type: "String", required: true, description: "用户ID" },
  { name: "key", type: "String", required: true, description: "API密钥" },
  {
    name: "orderNo",
    type: "String",
    required: true,
    description: "订单编号 (如: ORD1992226273535578112)",
  },
];

// 错误码列表
const errorCodes = [
  { code: "1", message: "操作成功", solution: "-" },
  { code: "-1", message: "操作失败", solution: "检查参数和网络连接" },
  { code: "-2", message: "参数错误", solution: "检查必填参数是否完整" },
  {
    code: "-100",
    message: "未登录或token已过期",
    solution: "重新登录获取token",
  },
  { code: "-200", message: "余额不足", solution: "充值后再试" },
  { code: "-204", message: "API接口未开通", solution: "先开通API密钥" },
  { code: "-205", message: "API密钥无效", solution: "检查uid和key是否正确" },
  { code: "-206", message: "订单已存在", solution: "避免重复下单" },
  { code: "-207", message: "订单不存在", solution: "检查订单ID是否正确" },
];

// 格式化JSON
const formatJson = (data) => {
  return JSON.stringify(data, null, 2);
};

// 测试函数
const testGetMoney = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  loading.getMoney = true;
  try {
    const res = await externalApi.getMoney({
      uid: credentials.uid,
      key: credentials.key,
    });
    results.getMoney = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.getMoney = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.getMoney = false;
  }
};

const testGetPlatforms = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  loading.getPlatforms = true;
  try {
    const res = await externalApi.getPlatforms({
      uid: credentials.uid,
      key: credentials.key,
    });
    results.getPlatforms = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.getPlatforms = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.getPlatforms = false;
  }
};

const testQueryCourses = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  if (
    !testForms.queryCourses.platform ||
    !testForms.queryCourses.user ||
    !testForms.queryCourses.pass
  ) {
    ElMessage.warning("请填写平台ID、学生账号和密码");
    return;
  }

  loading.queryCourses = true;
  try {
    const res = await externalApi.queryCourses({
      uid: credentials.uid,
      key: credentials.key,
      ...testForms.queryCourses,
    });
    results.queryCourses = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.queryCourses = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.queryCourses = false;
  }
};

const testAddOrder = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  if (
    !testForms.addOrder.platform ||
    !testForms.addOrder.user ||
    !testForms.addOrder.pass ||
    !testForms.addOrder.kcname
  ) {
    ElMessage.warning("请填写平台ID、学生账号、密码和课程名称");
    return;
  }

  loading.addOrder = true;
  try {
    const res = await externalApi.createOrder({
      uid: credentials.uid,
      key: credentials.key,
      ...testForms.addOrder,
    });
    results.addOrder = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.addOrder = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.addOrder = false;
  }
};

const testChadan = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  if (!testForms.chadan.username) {
    ElMessage.warning("请填写学生账号");
    return;
  }

  loading.chadan = true;
  try {
    const res = await externalApi.queryOrders({
      uid: credentials.uid,
      key: credentials.key,
      username: testForms.chadan.username,
    });
    results.chadan = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.chadan = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.chadan = false;
  }
};

const testQueryProgress = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  if (!testForms.queryProgress.orderNo) {
    ElMessage.warning("请填写订单编号");
    return;
  }

  loading.queryProgress = true;
  try {
    const res = await externalApi.queryProgress({
      uid: credentials.uid,
      key: credentials.key,
      orderNo: testForms.queryProgress.orderNo,
    });
    results.queryProgress = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.queryProgress = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.queryProgress = false;
  }
};

const testBudan = async () => {
  if (!credentials.uid || !credentials.key) {
    ElMessage.warning("请先填写并保存UID和API密钥");
    return;
  }

  if (!testForms.budan.orderNo) {
    ElMessage.warning("请填写订单编号");
    return;
  }

  loading.budan = true;
  try {
    const res = await externalApi.retryOrder({
      uid: credentials.uid,
      key: credentials.key,
      orderNo: testForms.budan.orderNo,
    });
    results.budan = res;
    ElMessage.success("请求成功");
  } catch (error) {
    results.budan = error.response?.data || error;
    ElMessage.error("请求失败: " + (error.message || "未知错误"));
  } finally {
    loading.budan = false;
  }
};
</script>

<style scoped>
.api-docs-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

.header-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: 600;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.usage-info {
  line-height: 1.8;
}

.usage-info p {
  margin: 5px 0;
}

.usage-info code {
  background: var(--bg-body);
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
  color: var(--color-danger);
}

.api-key-section {
  margin-top: 20px;
  padding: 20px;
  background: var(--bg-body);
  border-radius: 8px;
}

.api-list {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.api-card {
  transition: all 0.3s ease;
  border: 1px solid var(--border-color);
}

.api-card:hover {
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.api-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  cursor: pointer;
  user-select: none;
}

.method-tag {
  margin-right: 10px;
  font-weight: bold;
}

.api-title {
  font-weight: 600;
  font-size: 16px;
  margin-right: 10px;
}

.api-path {
  font-family: monospace;
  color: var(--text-regular);
  font-size: 14px;
}

.rotate-icon {
  transform: rotate(90deg);
  transition: transform 0.3s ease;
}

.api-content {
  padding-top: 20px;
}

.api-content h4 {
  margin: 20px 0 10px 0;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  padding-left: 10px;
  border-left: 4px solid var(--color-primary);
}

.api-description p {
  color: var(--text-regular);
  line-height: 1.6;
}

.api-params {
  margin-top: 20px;
}

.api-test {
  margin-top: 20px;
}

.api-result {
  margin-top: 20px;
  background: var(--bg-body);
  border-radius: 6px;
  padding: 15px;
}

.api-result pre {
  margin: 0;
  overflow-x: auto;
}

.api-result code {
  font-family: "Courier New", Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-primary);
}

.api-examples {
  margin-top: 20px;
}

.api-examples pre {
  background: #282c34;
  color: #abb2bf;
  padding: 15px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 10px 0 0 0;
}

.api-examples code {
  font-family: "Courier New", Courier, monospace;
  font-size: 13px;
  line-height: 1.6;
}

.error-codes-card {
  margin-top: 20px;
}

/* 响应式优化 */
@media (max-width: 768px) {
  .api-docs-page {
    padding: 10px;
  }

  .api-key-section {
    padding: 10px;
  }

  .api-path {
    display: block;
    margin-top: 5px;
    font-size: 12px;
  }

  .api-result pre,
  .api-examples pre {
    font-size: 11px;
  }
}

/* Dark Mode Overrides */
html.dark .api-key-section {
  background: #1e1e1e;
}

html.dark .api-result {
  background: #1e1e1e;
}

html.dark .usage-info code {
  background: #1e1e1e;
}

html.dark .api-card {
  background: var(--bg-card);
  border-color: var(--border-color);
}

html.dark .api-card:hover {
  background: var(--bg-card-hover);
}
</style>
