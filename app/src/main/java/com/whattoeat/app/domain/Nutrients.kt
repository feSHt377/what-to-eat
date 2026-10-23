package com.whattoeat.app.domain

/**
 * 一份食物的营养构成（饿了吗？先看这个类）。
 *
 * 约定：[Nutrients] 里的数值永远是「这一份 / 这一次吃了多少」的**绝对量**，
 * 而不是「每 100 克」。需要按 100 克换算时用 [scaledTo]。
 *
 * 单位统一：热量 kcal，其余为克（g）。
 */
data class Nutrients(
    val kcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val fatG: Double = 0.0,
    val carbG: Double = 0.0,
    /** 膳食纤维（g）。不提供热量，但和饱腹感、肠道健康关系很大，单列一项 */
    val fiberG: Double = 0.0,
) {
    /** 加总：两餐的营养素相加 */
    operator fun plus(other: Nutrients): Nutrients = Nutrients(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        fatG = fatG + other.fatG,
        carbG = carbG + other.carbG,
        fiberG = fiberG + other.fiberG,
    )

    /** 乘一个倍数（比如吃 1.5 份） */
    operator fun times(factor: Double): Nutrients = Nutrients(
        kcal = kcal * factor,
        proteinG = proteinG * factor,
        fatG = fatG * factor,
        carbG = carbG * factor,
        fiberG = fiberG * factor,
    )

    /**
     * 本对象若表示「每 100 克」的营养值，换算成实际吃了 [grams] 克 的营养值。
     */
    fun scaledTo(grams: Double): Nutrients = this * (grams / 100.0)

    fun isEmpty(): Boolean =
        kcal <= 0.0 && proteinG <= 0.0 && fatG <= 0.0 && carbG <= 0.0 && fiberG <= 0.0

    companion object {
        val ZERO = Nutrients()

        /** 构造一个「每 100 克含多少」的营养值，纯粹为了可读性 */
        fun per100g(
            kcal: Double,
            proteinG: Double,
            fatG: Double,
            carbG: Double,
            fiberG: Double = 0.0,
        ): Nutrients = Nutrients(kcal, proteinG, fatG, carbG, fiberG)

        /** 把一堆营养素加起来 */
        fun sum(items: Iterable<Nutrients>): Nutrients = items.fold(ZERO) { acc, n -> acc + n }
    }
}
