package dev.mite.core;

/** Tool shapes: component count (ingots per tool) and wear rates (EF-OUT-05, EF-OUT-06). */
public enum ToolType {
    //          components  blockRate      attackRate
    SHOVEL(     1, 0.5f,          1.0f),
    HATCHET(    1, 4f / 3f,       4f / 3f),
    KNIFE(      1, 2.0f,          0.5f),
    DAGGER(     1, 2.0f,          0.5f),
    CUDGEL(     1, 0.25f,         0.25f),    // gourdin (ItemCudgel)
    SWORD(      2, 2.0f,          0.5f),
    HOE(        2, 2.0f,          2.0f),
    SHEARS(     2, 1.0f,          2.0f),
    CLUB(       2, 0.25f,         0.25f),    // massue (ItemClub)
    PICKAXE(    3, 1.0f,          1.0f),
    AXE(        3, 1.0f,          1.0f),
    BATTLE_AXE( 4, 1.25f,         0.75f),
    MATTOCK(    4, 0.8f,          1.0f),
    WAR_HAMMER( 5, 2f / 3f,       2f / 3f),
    SCYTHE(     2, 2.0f,          1.0f),    // rates [INFÉRÉ]: absent from EF-OUT-06; hoe block rate, attack rate 1
    SPEAR(      1, 2.0f,          0.5f);    // [HORS-MITE] vanilla 1.21.11 spear (arbitrage n°22): 1 ingot, dagger rates [INFÉRÉ]

    public final int components;
    public final float blockRate;
    public final float attackRate;

    ToolType(int components, float blockRate, float attackRate) {
        this.components = components;
        this.blockRate = blockRate;
        this.attackRate = attackRate;
    }
}
