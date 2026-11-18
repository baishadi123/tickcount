# TickCount DESIGN.md — 视觉宪法

灵感谱系：Oura（纸语）、Starbucks（庭园）、Airbnb（珊瑚）的 DESIGN.md 提炼，
经 awesome-design-md 方法论吸收，落在 TickCount 自己的玻璃世界。

## 第一定律：每套主题是一个世界，不是一个色相

页面必须有自己旗帜鲜明的颜色（奶油纸/咖啡桌/雾蓝纸），卡片从世界里浮起，
墨水属于同一个世界。禁止"白底漂一点色"。

## 六世界（枚举名不变，显示名如下）

| 枚举 | 中文 | 英文 | 画布 | 主色 |
|---|---|---|---|---|
| WHITE | 白 | White | 纯白 | 近黑墨 |
| BLACK | 夜航 | Night | 咖啡黑 #1D1712 | 金 #DDAA61，字奶油 #F3EAD9 |
| TEAL | 庭园 | Garden | 咖啡奶油画布 #F2F0EB | 大地绿 #00754A |
| MIST | 静海 | Still sea | 雾蓝纸 #E7EDF2 | 海蓝 #23689E |
| AMBER | 纸语 | Paper | 奶油纸 #F7F1E8 | 陶土 #6B443D，选中金纸 #DDBE78 |
| ROSE | 玫瑰 | Rose | 樱花粉纸 #FEF0F2 | 石榴红 #8C2D44，选中浅樱 #F5DEE4 |
| CUSTOM | 自定义 | Custom | 染纸（hue 泡进纸里）；可切换染夜（同一 hue 泡进咖啡桌） | hsl(hue, .50, .36) / 夜版 hsl(hue, .45, .72) |

真身：`ui/theme/Schemes.kt`。改色先读它，勿凭记忆。

## 不可动摇的结构（六世界共享）

- **分层玻璃**：大卡全玻璃 / 编辑静板（interactive=false）/ 小件薄纱。
  玻璃层吃 MaterialTheme.colorScheme，主题换世界、玻璃不换语法。
- **每屏一个主角**：一条倒数日一张卡，大数字是 accent 的舞台。
- **墨不纯黑**：每世界的 ink 都带本世界的色（espresso/深海/咖啡），灰是走错门的客人。
- **事件自带色**（eventHueColor）是内容不是主题——各自染色、洗白 10%（EVENT_WASH_ALPHA），
  永远不覆盖世界的墨。

## Do

- 新增界面：页面用 scheme.background，卡片用 surfaceContainer 系，强调只用 primary。
- 选色犹豫时问：这张纸是什么做的？（奶油/陶瓷/雾/咖啡木）

## Don't

- 不引入第七种中性灰——每世界的 outline/outlineVariant 已配好。
- 不做"白底+彩按钮"——那是被推翻的旧世界。
- 深色不是黑底换灰字——夜航是咖啡桌+金+奶油，有自己的身体。
