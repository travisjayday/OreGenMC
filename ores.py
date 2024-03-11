types = {
        "REDSTONE_ORE" : 'Redstone',
        "NETHER_QUARTZ_ORE" : 'Nether Quartz',
        "NETHER_GOLD_ORE" : 'Nether Gold',
        "LAPIS_ORE" : 'Lapiz',
        "IRON_ORE" : 'Iron',
        "GOLD_ORE" : 'Gold',
        "EMERALD_ORE" : 'Emerald',
        "DIAMOND_ORE" : 'Dia',
        "COPPER_ORE" : 'Kupfer',
        "COAL_ORE": 'Kohle'
}

for typ, ore in types.items():
    print('public String msg_' + typ + " = \"&7[&l&fOreGen&r&7] &4Du hast &l&b" + ore + '&r&4 cool-down für &eMINUTESM SECONDSS\";')

for typ, ore in types.items():
    print('public Integer cooldown_' + typ + ' = 10')

