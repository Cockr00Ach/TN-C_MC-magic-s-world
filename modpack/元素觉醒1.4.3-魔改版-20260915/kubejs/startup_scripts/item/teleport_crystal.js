StartupEvents.registry("item", event => {
  const crystals = [
    { id: "primordial", name: "原始洞穴传送水晶" },
    { id: "abyssal", name: "渊海陷窟传送水晶" },
    { id: "forlorn", name: "异寂空谷传送水晶" },
    { id: "candy", name: "糖果龋洞传送水晶" },
    { id: "toxic", name: "毒化洞穴传送水晶" },
    { id: "magnetic", name: "磁场洞穴传送水晶" }
  ];

  crystals.forEach(crystal => {
    event.create(crystal.id + "_teleport_crystal")
      .displayName(crystal.name)
      .rarity("epic") 
      .maxStackSize(1);
  });
});