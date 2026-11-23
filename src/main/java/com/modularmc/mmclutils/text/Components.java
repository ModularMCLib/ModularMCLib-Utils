package com.modularmc.mmclutils.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collector;

/** 组合原版文本组件，保留子组件的样式和翻译信息。 */
public final class Components {

    private Components() {}

    public static MutableComponent join(Iterable<? extends Component> components, Component separator) {
        Iterator<? extends Component> iterator = components.iterator();
        MutableComponent result = Component.empty();
        if (iterator.hasNext()) result.append(iterator.next());
        while (iterator.hasNext()) result.append(separator).append(iterator.next());
        return result;
    }

    public static Collector<Component, ?, MutableComponent> joining(Component separator) {
        return Collector.of(
                ArrayList<Component>::new,
                List::add,
                (left, right) -> {
                    left.addAll(right);
                    return left;
                },
                parts -> join(parts, separator));
    }
}
