# Mechanical conversion of our original guide text into native FTB chapter data.
# Run with PowerShell 7. No live pack, third-party chapter or save is modified.
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$book=Get-Content -LiteralPath (Join-Path $repo 'src\main\resources\assets\tnc\guide\gameplay.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$dest=Join-Path $repo 'questbook\ftbquests'
$chapterDir=Join-Path $dest 'chapters'
New-Item -ItemType Directory -Path $chapterDir -Force | Out-Null
$utf8=[Text.UTF8Encoding]::new($false)
$groupId='544E434755494445'
$group=[ordered]@{id=$groupId;title='&b&lTN-C - 玩法指南';collapse=$false}
[IO.File]::WriteAllText((Join-Path $dest 'group.snbt'),($group | ConvertTo-Json -Depth 5),$utf8)
$icons=@('minecraft:compass','minecraft:stick','minecraft:experience_bottle','minecraft:iron_sword','minecraft:book','minecraft:emerald','minecraft:bread','minecraft:oak_door','minecraft:crafting_table')
function New-ReadingNode([int]$page,[int]$number,[string]$title,[string[]]$description,[string]$icon,[double]$x,[double]$y,[double]$size=1){
    return [ordered]@{
        id=('544E4302{0:X2}{1:X6}' -f $page,$number)
        title=$title;icon=$icon;x=$x;y=$y;size=$size;shape='square'
        description=$description
        tasks=@([ordered]@{id=('544E4303{0:X2}{1:X6}' -f $page,$number);type='checkmark';title='阅读本节（不发放玩法奖励）'})
        rewards=@();dependencies=@()
    }
}
for($i=0;$i -lt $book.pages.Count;$i++){
    $page=$book.pages[$i];$number=$i+1;$nodes=@();$images=@()
    $intro=@(('&b&l'+$page.title),('&7'+$page.subtitle),'',('&e'+$page.status),'',
        '&f点击章节中的节点阅读说明。勾选只表示看过本节，不代表完成升级、买房或委托。',
        '&7真实目标请查看“启程”和“归处”章节；各系统开放范围见本页状态。')
    $nodes+=New-ReadingNode $number 0 ('&b&l'+$page.title) $intro $icons[$i] 0 0 1.8
    $s=0
    foreach($section in $page.sections){
        $description=@(('&e'+$page.status),'',('&b&l'+$section.heading),'')
        $description+=@($section.text -split '\r?\n' | ForEach-Object { '&f'+$_ })
        $x=-6.0+($s%5)*3.0
        $y=3.0+[math]::Floor($s/5)*3.0
        $nodes+=New-ReadingNode $number ($s+1) $section.heading $description $icons[$i] $x $y
        $s++
    }
    $n=0
    foreach($image in $page.illustrations){
        $x=-6.0+$n*3.0
        $images+= [ordered]@{image=$image.texture;x=$x;y=-4.0;width=1.5;height=1.5;rotation=0.0}
        $nodes+=New-ReadingNode $number (100+$n) $image.title @('&e武器外观方向 · 基础匠人订单已开放','',('&f'+$image.description),'',
            '&7高四档武器承载与专属材料继续建设，当前产出兼容原法术的基础法杖。') 'minecraft:stick' $x -2.0 0.75
        $n++
    }
    $filename='tnc_guide_{0:D2}_{1}' -f $number,$page.id
    $chapter=[ordered]@{
        id=('544E4301{0:X2}000000' -f $number);filename=$filename;group=$groupId
        title=('{0:D2} - {1}' -f $number,$page.title);icon=$icons[$i];order_index=(-100+$i)
        default_hide_dependency_lines=$true;default_quest_shape='square';quest_links=@()
        images=$images;quests=$nodes
    }
    [IO.File]::WriteAllText((Join-Path $chapterDir ($filename+'.snbt')),($chapter | ConvertTo-Json -Depth 20),$utf8)
}
Write-Output "Generated $($book.pages.Count) original native FTB chapters in questbook/ftbquests."
