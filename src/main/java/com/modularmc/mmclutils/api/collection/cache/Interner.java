package com.modularmc.mmclutils.api.collection.cache;

import java.util.function.UnaryOperator;

/**
 * 按相等关系或指定策略合并对象实例，方便调用方复用不可变值。
 * 参与比较和哈希的对象状态在驻留期间须保持稳定。弱引用实现允许无外部引用的实例被回收。
 *
 * @param <T> 可驻留的对象类型
 */
public interface Interner<T> {

    /** 共享的原样返回函数。 */
    UnaryOperator<Object> IDENTITY_OPERATOR = UnaryOperator.identity();

    /**
     * 返回将输入原样用作驻留实例的函数。
     *
     * @param <T> 驻留对象类型
     * @return 原样返回函数
     */
    @SuppressWarnings("unchecked")
    static <T> UnaryOperator<T> identityMappingFunction() {
        return (UnaryOperator<T>) IDENTITY_OPERATOR;
    }

    /**
     * 查找与样本等价的驻留实例，缺失时登记样本。
     *
     * @param sample 用于查找的非空样本
     * @return 该等价类的共享实例
     */
    T intern(final T sample);

    /**
     * 查找与样本等价的实例，缺失时允许调用方用复制品参与驻留。
     *
     * @param sample          用于查找的非空样本
     * @param mappingFunction 生成非空实例的函数，结果须与样本等价并使用相同哈希
     * @return 该等价类的共享实例
     */
    T intern(final T sample, UnaryOperator<T> mappingFunction);

    /**
     * 查询是否已有与样本等价且仍可访问的实例。
     *
     * @param sample 用于查找的非空样本
     * @return 存在可用驻留实例时为真
     */
    boolean isPresent(final T sample);

    /**
     * 仅在缺少等价实例时登记样本。
     *
     * @param sample 供驻留的非空样本
     * @return 本次操作登记了样本时为真
     */
    boolean addIfAbsent(final T sample);

    /** 清除当前驻留记录。调用方此前取得的实例可继续使用。 */
    void clear();
}
