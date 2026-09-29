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
        '&7真实目标请查看七个玩法章节；各系统开放范围见本页状态。')
    $nodes+=New-ReadingNode $number 0 ('&b&l'+$page.title) $intro $icons[$i] 0 0 1.8
    $s=0
    foreach($section in $page.sections){
        $description=@(('&e'+$page.status),'',('&b&l'+$section.heading),'')
        $description+=@($section.text -split '\r?\n' | ForEach-Object { '&f'+$_ })
        $x=$(if($i -eq 0){-4.0+($s%3)*4.0}elseif($i -eq 1){-3.0+($s%2)*6.0}else{-6.0+($s%5)*3.0})
        $y=$(if($i -eq 0){3.0+[math]::Floor($s/3)*2.6}elseif($i -eq 1){2.5+[math]::Floor($s/2)*2.6}else{3.0+[math]::Floor($s/5)*3.0})
        $nodes+=New-ReadingNode $number ($s+1) $section.heading $description $icons[$i] $x $y
        $s++
    }
    $n=0
    foreach($image in $page.illustrations){
        $x=-4.0+$n*2.0
        $images+= [ordered]@{image=$image.texture;x=$x;y=-3.0;width=1.8;height=1.8;rotation=0.0}
        $nodes+=New-ReadingNode $number (100+$n) $image.title @('&e七系五阶订单已开放','',('&f'+$image.description),'',
            '&7水系五阶均为实际物品；全部七系路线请看“提升之路·七系法器”。') ('tnc:water_wand_'+($n+1)) $x -1.6 0.85
        $n++
    }
    $filename='tnc_guide_{0:D2}_{1}' -f $number,$page.id
    $chapter=[ordered]@{
        id=('544E4301{0:X2}000000' -f $number);filename=$filename;group=$groupId
        title=('{0:D2} - {1}' -f $number,$page.title);icon=$icons[$i];order_index=$(if($i -eq 0){-200}else{-100+$i})
        default_hide_dependency_lines=$true;default_quest_shape='square';quest_links=@()
        images=$images;quests=$nodes
    }
    if($i -eq 0){
        $nodes[0].title='&6&l归 · 吴归衡'
        $nodes[0].icon='minecraft:compass'
        $nodes[0].description=@('&6&l归 · 吴归衡','','&f普通市井家庭的少年，七系亲和皆为三，没有单一主修。','&f挚友衍不告而别后，他从酒馆出发，去寻找那个没有说清的答案。','','&7这片世界有剑、铠甲、魔法与未知。远行之外，酒馆、工坊和家也为你留着归处。')
        $castIcons=@('minecraft:shield','minecraft:enchanted_book','tnc:zuowang_spawn_egg','minecraft:amethyst_shard','tnc:self_spawn_egg','minecraft:anvil')
        for($c=0;$c -lt 6;$c++){$nodes[$c+1].icon=$castIcons[$c];$nodes[$c+1].size=1.2}
        $chapter.title='&6&l歸 · 世界';$chapter.images+= [ordered]@{image='tnc:textures/guide/gui_world_title.png';x=0.0;y=-5.0;width=12.0;height=4.2;rotation=0.0}}
    [IO.File]::WriteAllText((Join-Path $chapterDir ($filename+'.snbt')),($chapter | ConvertTo-Json -Depth 20),$utf8)
}
Write-Output "Generated $($book.pages.Count) original native FTB chapters in questbook/ftbquests."
