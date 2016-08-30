/*
 * The MIT License
 *
 * Copyright (c) 2016 Zhixun Tan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package arrow.repr

import arrow._
import scala.collection.mutable.ArrayBuffer

class Repr {
    trait Subscription {
        val from: OutUntyped
        val to: InUntyped

        override def toString = s"$from -> $to"
    }

    class SubscriptionImpl[T](val from: Out[T], val to: In[T])
        extends Subscription

    class SubscriptionRImpl[RT, T](val from: Out[RT], val to: In[T])
                                  (implicit rT: RT <:< R[T])
        extends Subscription

//    trait Processor
//
//    trait OneToOneProcessor[I, O] extends Processor
//
//    trait OneToOneProcessorImpl[I, O] extends OneToOneProcessor[I, O] {
//        val node: Node[I, O]
//    }
//
//    trait OneToOneProcessorRImpl[I, O] extends OneToOneProcessor[I, O] {
//        val node: Node[I, R[O]]
//    }
//
//    trait OneToListProcessor[I, O] extends Processor
//
//    trait OneToListProcessorImpl[I, O] extends OneToListProcessor[I, O] {
//        val node: Node[I, List[O]]
//    }
//
//    trait OneToListProcessorRImpl[I, O] extends OneToListProcessor[I, O] {
//        val node: Node[I, List[R[O]]]
//    }
//
//    trait ListToOneProcessor[I, O] extends Processor
//
//    trait ListToOneProcessorImpl[I, O] extends ListToOneProcessor[I, O] {
//        val node: Node[List[I], O]
//    }
//
//    val processors = ArrayBuffer.empty[Processor]

    def insertNode(node: NodeUntyped) = {
        nodes(node) = true
    }

    def insertSubscription(subscription: Subscription) = {
        subscriptions(subscription) = true
    }

    val nodes = collection.mutable.Set.empty[NodeUntyped]
    val subscriptions = collection.mutable.Set.empty[Subscription]
}