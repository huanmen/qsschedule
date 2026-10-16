# 官方考试通知聚合服务

这是独立的公开信息服务，不接收校园账号、个人成绩或教务会话。本轮只在 HTML 中演示，尚未接入 Android APP。

## 数据与同步

公开 GET 接口：`https://huanmen.com.cn/qsschedule/api/exam-notices`。无需账号，支持跨域读取公开通知。刷新客户端只同步服务器缓存，不触发远程网站抓取，避免用户数量增长造成学校网站请求激增。

`qsschedule-exams.timer` 每小时抓取，随机延迟最多 3 分钟；服务器停机错过的任务会在恢复后补跑。每个来源单独记录 `checkedAt`、`lastSuccessAt`、`httpStatus`、`state`、`articleErrors` 和 `stale`。超过 24 小时没有成功读取来源索引标记为过期。已抓正文每 6 小时复核；新文章及失败的正文在下一次抓取处理。首页 ETag/Last-Modified 可用时采用条件请求。

来源为泉师教务处考试报名专栏、教育部教育考试院四六级及计算机等级网站、福建省教育考试院非学历考试栏目。全国通知适用泉州，省级报名通告优先由用户核对，本校普通话通知与社会测试站计划不混为一谈。当前没有接入普通话报名系统动态测试站列表，不能声称覆盖福建所有普通话批次。

每次最多读取每个来源索引的 20 篇相关通知，保留最近 300 条历史通知；这不是全站历史归档。标题分为四六级、计算机等级、普通话和教师资格。学校普通期末考试及个人考场安排不在此公开信息服务范围。

## 准确性与故障处理

- 索引零匹配或条目数量异常骤减视为结构变化，保留上次结果。
- 网络请求限制 15 秒、2 MB、一次重试，来源间隔请求；重定向及文章链接仅允许同一官方主机的 HTTPS 地址。
- 正文为空、模板字段缺失与网络失败独立标记，不以空结果清空已抓内容。正文失败保留已抓摘要和原文入口。
- 同一官方 URL 使用固定 ID；正文变化更新内容摘要和哈希，不制造重复通知。
- 图片、Webplus PDF 播放器与附件保留原文链接，**不下载学生名单或附件内容**。
- `publishedAt` 只是发布日期，不是考试日期。`examDate` 当前为 `null`，`dateStatus` 为 `review_required`。不能直接把自动收集通知当成已确认考试安排或生成倒计时。
- HTML 中的“已核实日程”仍是人工核实的预览数据，与服务器“官方通知”分开显示。
- 结果使用 fsync 和原子替换，API 读取完整快照；磁盘 JSON 损坏时任务报错，不覆盖损坏文件。
- `degraded` 包含读取失败、部分正文失败、过期状态。抓取任务返回非零状态，systemd/journal 可以发现故障；API 仍提供最近成功内容。

学校改版、停机、验证码或访问限制时无法保证持续成功。目前未配置短信或邮件告警；查看来源状态与 journal 即可定位，后续正式接入可加运维告警。不能承诺自动读懂所有图片/附件或马上发现整站新版路径。

## 部署与运维

运行依赖锁定在 `exam-requirements.txt`，独立环境 `/opt/qsschedule/exam-venv`。API 使用标准 Python，无需切换已有解释器。服务按 `qsschedule` 用户运行，仅读写 `/var/lib/qsschedule`；不读取保存统计盐的环境文件。API 只增加公开读接口，原下载及公告接口保留。

`deploy-exams.sh` 需在服务器 `/tmp/qss-exams-*` 暂存目录运行，目录包括 `app.py`、`admin.py`、`exam_collector.py`、依赖清单、测试及两个 systemd 文件。脚本先安装独立依赖、运行测试，备份现有 API 再更新，并启用计时器。只有经过审查确认暂存的 `app.py` 包含服务器现有功能时才执行。

```sh
systemctl list-timers qsschedule-exams.timer
systemctl status qsschedule-exams.service
journalctl -u qsschedule-exams.service -n 30 --no-pager
systemctl start qsschedule-exams.service
curl --fail https://huanmen.com.cn/qsschedule/api/exam-notices
```

关闭抓取可执行 `systemctl disable --now qsschedule-exams.timer`。缓存保留；恢复旧 API 时使用部署前生成的 `/opt/qsschedule/app.py.before-exams-*` 备份，并重启 `qsschedule.service`。

本地校验：`python -m unittest discover -s server/qsschedule -p 'test_*.py' -v`。测试使用合成 HTML，覆盖重复记录、网站改版、网络失败、正文变化、播放器附件、缓存恢复、304、旧数据标记与原子保存。
