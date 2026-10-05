开机自启清理记录
================================================================
清理时间：2026-09-27 00:36（本机时间）
操作者：DSH 会话（管理员权限，经 UAC 确认）

一、已删除的自启项（注册表 Run）

HKCU\Software\Microsoft\Windows\CurrentVersion\Run
  - BaiduYunDetect                百度网盘检测服务（原本已被任务管理器禁用）
  - CanvaAutoLaunchAvailabilityCheckAgent   Canva 可画自启（原本已禁用）
  - MicrosoftEdgeAutoLaunch_A6F4C952B85A158E081C9579C0E0EAB9   Edge 随会话启动（原本已禁用）

HKLM\SOFTWARE\Wow6432Node\Microsoft\Windows\CurrentVersion\Run
  - SunJavaUpdateSched            Java 自动更新（jusched.exe）
  - DesktopPortal                 诺娃桌面 N0vaDesktop
  - RadminVPN                     Radmin VPN（/minimized）
  - Adobe CCXProcess              Adobe Creative Cloud Experience
  - ControlCenter4                Brother 打印 ControlCenter4
  - BrStsMon00                    Brother 状态监视器
  - M15A                          Brother 扫描/驱动启动项

二、已从启动文件夹移走的快捷方式（全用户启动目录）

  - SOLIDWORKS 2024 快速启动.lnk
  - SOLIDWORKS 后台下载程序.lnk
  （原件已移动到 lnk\removed_*.lnk，未删除）

三、已禁用的登录触发计划任务（保留任务本身，只置为禁用）

  - OneDrive Startup Task-S-1-5-21-620344409-4013399299-2564790692-1001
  - WpsUpdateLogonTask_FDCX
  - WpsWakeWnsLogonTask
  - QuarkCloudDriveUpdaterTaskUser1.0.0.11{...}
  - QuarkUpdaterTaskUser1.0.0.21{...}

四、刻意没有动的项

  - HuionTablet                    绘王数位板驱动（本次未勾选，保留）
  - ctfmon                         输入法框架（系统）
  - SecurityHealth                 Windows 安全中心（系统）
  - RtkAudUService                 Realtek 音频服务（系统）

五、发现的残留记录（对应的 Run 值早已不存在，未处理，无实际影响）

  HKCU/HKLM StartupApproved 里仍留有 ACE-Tray、SunloginClient、
  AdobeAAMUpdater-1.0、AdobeCS5.5ServiceManager、vmware-tray.exe、Ollama.lnk
  等历史条目，它们只是"某个程序曾在这里自启"的记录，不会启动任何东西。

六、如何还原

  右键"以管理员身份运行"本目录下的 restore.ps1，它会：
    1) 导回 6 个 .reg 备份（Run 与 StartupApproved 键）
    2) 把 lnk\removed_*.lnk 复制回全用户启动文件夹
    3) 重新启用上面 5 个计划任务
  执行结果写入 restore-log.txt。

七、备份清单

  HKCU_Run.reg / HKLM_Run.reg / HKLM_Run32.reg
  HKCU_StartupApproved_Run.reg / HKLM_StartupApproved_Run.reg / HKLM_StartupApproved_Run32.reg
  lnk\*.lnk（SOLIDWORKS 两个快捷方式的原件 + removed_ 前缀的副本）
  tasks\*.xml（计划任务的导出定义）
  remove-elevated.ps1（本次使用的提权清理脚本，可留档）
  elevated-log.txt / restore.ps1 / restore-log.txt
