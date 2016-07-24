# Akka ExecutionContext Test

# Disclaimer
This is just for my study. Threading, pools and configuration of them is some what of an art as I understand it and I 
am far from being an expert. That said, there are some great minds in this fields, please refer to the sources section below.

# Source
- [Doug Lea - Fork/Join Parallelism in Java](http://gee.cs.oswego.edu/dl/cpjslides/fj.pdf)
- [Robert Stewart and Jeremy Singer - Comparing Fork/Join and MapReduce](http://www.macs.hw.ac.uk/cs/techreps/docs/files/HW-MACS-TR-0096.pdf)
- [Oracle - Fork/Join](http://www.oracle.com/technetwork/articles/java/fork-join-422606.html)
- [Scala Documentation - Futures](http://docs.scala-lang.org/overviews/core/futures.html)
- [Jessica K - Choosing an ExecutorService](http://blog.jessitron.com/2014/01/choosing-executorservice.html)
- [Jessica K - The global ExecutionContext makes your life easier](http://blog.jessitron.com/2014/02/scala-global-executioncontext-makes.html)
- [Daniel Westheide - The Neophyte's Guide to Scala Part 8: Welcome to the Future](http://danielwestheide.com/blog/2013/01/09/the-neophytes-guide-to-scala-part-8-welcome-to-the-future.html)
- [Daniel Westheide - The Neophyte's Guide to Scala Part 9: Promises and Futures in Practice](http://danielwestheide.com/blog/2013/01/16/the-neophytes-guide-to-scala-part-9-promises-and-futures-in-practice.html)
- [Akka Documentation - Futures](http://doc.akka.io/docs/akka/2.3.6/scala/futures.html)
- [Akka Documentation - Dispatchers](http://doc.akka.io/docs/akka/2.3.6/scala/dispatchers.html)
- [PlayFramework Configuration - Thread Pool Configuration](https://www.playframework.com/documentation/2.3.x/ThreadPools)

# First some definitions
I know, this is a copy/paste of a lot of resources on the Internet, but I found that having all the necessary information for 
this subject in one place reads really well. Better than switching browser tabs in any case! Please read the subject you 
do not know, they are all relevant for the discussion later.

## Thread
A [thread](http://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html) is a thread of execution in a program. 
The Java Virtual Machine allows an application to have multiple threads of execution running concurrently.

Every thread has a priority. Threads with higher priority are executed in preference to threads with lower priority. 
Each thread may or may not also be marked as a daemon. When code running in some thread creates a new Thread object, 
the new thread has its priority initially set equal to the priority of the creating thread, and is a daemon thread if 
and only if the creating thread is a daemon.

When a Java Virtual Machine starts up, there is usually a single non-daemon thread (which typically calls the method named 
main of some designated class). The Java Virtual Machine continues to execute threads until either of the following occurs:

* The exit method of class Runtime has been called and the security manager has permitted the exit operation to take place.
* All threads that are not daemon threads have died, either by returning from the call to the run method or by throwing an 
exception that propagates beyond the run method.

There are two ways to create a new thread of execution. One is to declare a class to be a subclass of Thread. This subclass 
should override the run method of class Thread. An instance of the subclass can then be allocated and started:

    class Foo extends Thread {
      override def run(): Unit = {
        Thread.sleep(1000)
        println("Done")
      }
    }

REPL:

    > val p = new Foo
    > p.start
    
The other way to create a thread is to declare a class that implements the Runnable interface. That class then implements 
the run method. An instance of the class can then be allocated, passed as an argument when creating Thread, and started. 
The same example in this other style looks like the following:

    class Foo extends Runnable {
      override def run(): Unit = {
        Thread.sleep(1000)
        println("Done")
      }
    }
    
    > new Thread(new Foo).start()

## How Cheap Are Threads?
According to [this](http://iwillgetthatjobatgoogle.tumblr.com/post/38381478148/why-using-many-threads-in-java-is-bad) source, 
threads are very expensive, based upon your OS, JVM implementation, every platform has its own limits and cost, but as a 
rule of thumb, for all platforms, having a lot of threads (thousands) is bad. Let's find out why:

### Thread lifecycle overhead
Thread creation and teardown are not free. The actual overhead varies across platforms, but thread creation takes time, 
introducing latency into request processing, and requires some processing activity by the JVM and OS. If requests are 
frequent and lightweight, as in most server applications, creating a new thread for each request can consume significant 
computing resources

### Context switch
When OS decides to switch to another thread execution, there is a context switch (all the registers and data are being pushed to
memory, CPU and RAM are being tuned to another thread, another thread restores its state agein etc). These operations aren’t 
too slow, but when there are hundreds or even thousands of threads, it can be a real disaster. OS will work inefficiently and 
most of time will be used to switch one context to another.

### Thread data
Each thread had its (own) stack (quite huge block, usually 256KB by default), its descriptors. Threads also may have 
ThreadLocal variables. With default settings only 4 threads consume 1 Mb of memory. It’s quite huge amount of memory!
Having 300 worker threads in a pool (we will look at this later) costs about 75MB of memory. 

### System overhead
Thread creation in Java forces the OS to create a memory block for stack and other data. It is done using system calls. 
That means that __for each thread at least one system native thread is being executed to prepare memory for it__. It also 
has some overhead.

### Use Thread Pools
Threads are a valuable resource, and knowing what we know now, it would be a shame to throw it away. Try thinking about
threads as your new shiny favorite dream car, you won't throw it away after just one ride would you? You would reuse it
and using thread pools, we can do just that.

## Runnable (Interface)
The [Runnable](http://docs.oracle.com/javase/8/docs/api/java/lang/Runnable.html) interface should be implemented by any class 
whose instances are intended to be executed by a thread. The class must define a method of no arguments called run.

This interface is designed to provide a common protocol for objects that wish to execute code while they are active. For example, 
Runnable is implemented by class Thread. Being active simply means that a thread has been started and has not yet been stopped.

In addition, Runnable provides the means for a class to be active while not subclassing Thread. A class that implements Runnable 
can run without subclassing Thread by instantiating a Thread instance and passing itself in as the target. In most cases, the 
Runnable interface should be used if you are only planning to override the run() method and no other Thread methods. 
This is important because classes should not be subclassed unless the programmer intends on modifying or enhancing the 
fundamental behavior of the class.

## Executor (Interface)
An [Executor](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Executor.html) is an object that executes 
submitted Runnable tasks. This interface provides a way of decoupling task submission from the mechanics of how each 
task will be run, including details of thread use, scheduling, etc. An Executor is normally used instead of 
explicitly creating threads.

For example, rather than invoking:
 
    new Thread(new(RunnableTask())).start() 
    
for each of a set of tasks, like we all have done, you might use:

    Executor executor = anExecutor;
    executor.execute(new RunnableTask1());
    executor.execute(new RunnableTask2());
    
Tasks are most often executed in some thread, other than the caller's thread. Tasks are also usually executed
asynchronously, but the Exectutor can also execute tasks immediately in the caller's thread, thus being synchronous.

## ExecutorService (Interface)
An [ExecutorService](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ExecutorService.html) is-an Executor
that provides methods to manage termination, and methods that can produce a Future for tracking progress of one or more 
asynchronous tasks.

An ExecutorService can be shut down, which will cause it to reject new tasks. Two different methods are provided for 
shutting down an ExecutorService. The shutdown() method will allow previously submitted tasks to execute before terminating, 
while the shutdownNow() method prevents waiting tasks from starting and attempts to stop currently executing tasks. 

Upon termination, an executor has no tasks actively executing, no tasks awaiting execution, and no new tasks can be submitted. 

An unused ExecutorService should be shut down to allow reclamation of its resources.

Method submit extends base method Executor.execute(Runnable) by creating and returning a Future that can be used to cancel 
execution and/or wait for completion. Methods invokeAny and invokeAll perform the most commonly useful forms of bulk execution, 
executing a collection of tasks and then waiting for at least one, or all, to complete.

## ExecutorService Implementations
For our study, there are two ExecutorService Implementations

1. The [ForkJoinPool](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ForkJoinPool.html)
2. The [ThreadPoolExecutor](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html)

## The ForkJoinPool (ExecutorService Implementation)
The Java fork/join framework provides support for fine-grained, recursive, divide and conquer parallelism. It is part
of Java sinds version 7 in late 2011.

The ''fork'' operation creates a new unit of work that may execute in parallel with its parent creator. The ''join'' operation
merges control once the parent (forker) and child (forked) tasks have both completed. The significance of using fork/join
parallelism is that is provides very efficient load balancing, if the subtasks are decomposed in such a way that they can be 
executed without dependencies. 

Fork/join parallelism is implemented by means of a fixed pool of worker threads. Each work thread can execute one thread
at a time. Tasks waiting to be executed are stored in a queue, which is owned by a particular worker thread. Currently executing
tasks can dynamically generate (fork) new tasks, which are then enqueued for subsequent execution. This is ideal for dynamic task
creation when we cannot determine beforehand how many task there wil be.

When a worker thread has completed execution of a particular task, it fetches the next task from its own queue. If the queue is empty
it can ''steal'' a task from another queue. This work stealing enables efficient load balancing. 

The Java fork/join framework targets single JVM shared-memory parallelism ie a multicore machine running a multi-threaded Java application
in a single JVM instance. The number of worker threads in a fork/join pool is generally upper-bounded by the number of cores in the system.

The dynamic load balancing is useful since some forked tasks may complete execution much quicker than others. For instance, 
the computation time may depend on values in the input data set, rather than just the size of the input data. There may be reasons
for some tasks to finish quicker than others, cache locality, turbo boosting, make some cores faster than others. This makes
makes dynamic load balancing ideal for multicore architectures. 

### What makes it special?
- Worker threads: Small number of threads (as possible) based upon number of cpu cores, normally one thread per core
- Work stealing: ''find an execute tasks'' that are submitted to the pool
- Best fit for event-style tasks that are never joined, for example with Akka Actors.
- Bad fit for: Blocking I/O operations (JDBC), number crunching code that by virtue of calculation uses the thread, 
because there are only a small number of threads, this can lead to thread starvation, which means there are no more
threads available and no more tasks can be executed, halting the system.
 
### Parallelism
The parallelism setting on the ForkJoinPool creates the number of worker threads it will hold in the pool to execute
tasks. By default this is equal to the number of detected CPUs on your computer. For example, on my computer:

    > new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool()).parallelismLevel
    res6: Int = 8
    
    > new ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(2)).parallelismLevel
    res7: Int = 2
    
### What is the best value to set the parallelism to?
According to [this](https://blogs.oracle.com/openomics/entry/java_fork_join_sparc_t5) research, on an Oracle T5-4 server 
which has 4 processors, each has 16 cores, with 8 threads contexts per core, that is 64 virtual CPUs per core, and looking
at the seconds to complete time, the best factor was having 32 worker threads per core, but that is on an Oracle T5-4.

The conclusion we have so far, is that just saying '1 thread per core' is best, is premature. It depends on the hardware
and the for/join problem implementation. For the Oracle T5-4 it was 2 threads per core. One could say that the factor '2'
is some kind of 'multiplier', because by default, the ForkJoin pool will set to 1 worker thread per core. But tuning the pool
to use 2 threads per core gave better performance.
 
Akka uses the Typesafe configuration to tune these kind of settings to your deployment environment hardware, and one of
these settings is the multiplier.

## The ThreadPoolExecutor (Implementation)
The [ThreadPoolExecutor](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ThreadPoolExecutor.html) is an 
ExecutorService that executes each submitted task using one of possibly several pooled threads, normally configured using 
Executors factory methods.

Thread pools address two different problems: they usually provide improved performance when executing large numbers of 
asynchronous tasks, due to reduced per-task invocation overhead, and they provide a means of bounding and managing the 
resources, including threads, consumed when executing a collection of tasks. Each ThreadPoolExecutor also maintains 
some basic statistics, such as the number of completed tasks.

To be useful across a wide range of contexts, this class provides many adjustable parameters and extensibility hooks. 
However, programmers are urged to use the more convenient Executors factory methods Executors.newCachedThreadPool() 
(unbounded thread pool, with automatic thread reclamation), Executors.newFixedThreadPool(int) (fixed size thread pool) 
and Executors.newSingleThreadExecutor() (single background thread), that preconfigure settings for the most common 
usage scenarios.

### What makes it special
- Configurable pool of resuable worker threads,
- Saves time spent in Thread lifecycle overhead

# Scala Future and the Execution Context

## Scala Future
According to [Daniel Westheide](http://danielwestheide.com/blog/2013/01/09/the-neophytes-guide-to-scala-part-8-welcome-to-the-future.html),
a `scala.concurrent.Future` is a container type, representing a computation that is supposed to eventually result in a value of type T. Alas, 
the computation might go wrong or time out, so when the future is completed, it may not have been successful after all, in which case it 
contains an exception instead.

Future is a write-once container – after a future has been completed, it is effectively immutable. Also, the Future type only provides an 
interface for reading the value to be computed. The task of writing the computed value is achieved via a Promise. Hence, there is a clear 
separation of concerns in the API design. 

The Future API looks like:

    object Future {
      def apply[T](body: => T)(implicit ec: ExecutionContext): Future[T]
    }
    
This means, that any block of code can be executed and returns a type T. Using currying, an implicit ExecutionContext must be available
for the Future to be executed.

## What is an Execution Context
According to [Daniel Westheide](http://danielwestheide.com/blog/2013/01/09/the-neophytes-guide-to-scala-part-8-welcome-to-the-future.html), 
an ExecutionContext is something that can execute a `scala.concurrent.Future`, and you can think of it as something like a thread pool. 

When imported implicitly, an ExecutionContext is available implicitly, so we only have a single one-element argument list remaining; the body to
be executed. In Scala, a single-argument lists can be enclosed with curly braces instead of parentheses. People often make use of this when 
calling the future method, making it look a little bit like we are using a feature of the language and not calling an ordinary method. 
The ExecutionContext is an implicit parameter for virtually all of the Future API.

Basically, an ExecutionContext is a wrapper around `java.util.concurrent.Executor` and `java.util.concurrent.ExecutorService`:

    object ExecutionContext {
     def fromExecutor(e: java.util.concurrent.Executor): ExecutionContextExecutor
     def fromExecutorService(e: java.util.concurrent.ExecutorService): ExecutionContextExecutorService
    }

## Akka ExecutionContext
According to the [Akka Futures Documentation](http://doc.akka.io/docs/akka/2.3.6/scala/futures.html), an ExecutionContext is needed
for executing Futures. A Future is a data structure used to retrieve the result of some concurrent operation. The result can be accessed 
synchronously (blocking) or asynchronously (non-blocking). An ExecutionContext is very similar to a `java.util.concurrent.Executor`; it is
a wrapper around one.

When using Actors, it is easy to have an ActorSystem in scope. By implicit(ly) importing `system.dispatcher`, the default Akka dispatcher 
is in scope as the ExecutionContext, but it is also easy to create one by using the factory methods provided by the ExecutionContext companion 
object to wrap java.util.concurrent.Executor and java.util.concurrent.ExecutorService. 

## ExecutionContext within Actors
Each actor is configured to be run on a `MessageDispatcher`, and that dispatcher doubles as an ExecutionContext. __If the nature of the 
Future call invoked by the actor matches or is compatible with the activities of that actor (e.g. all CPU bound and no latency requirements), 
then it may be easiest to reuse the dispatcher for running the Futures by importing context.dispatcher.__ (read this line again!)

    implicit val ec = ExecutionContext.fromExecutorService(java.util.concurrent.ExecutorService)

But which ExecutionService to use?
  
    import java.util.concurrent.Executors
    import scala.concurrent.{ExecutionContext, Future}
    
    val es = Executors.newFixedThreadPool(20)
    implicit val ec = ExecutionContext.fromExecutorService(es)
    
    Future {
      Thread.sleep(1000)
      println("Hello")
    }
  
## Which ExecutionServices are there?
The `java.util.concurrent.Executors` class has the following factory methods:

* __newFixedThreadPool(nThreads: Int): ExecutorService__ => Creates a thread pool that reuses a fixed number of threads 
operating off a shared unbounded queue. At any point, at most `nThreads` will be active processing tasks. If additional 
tasks are submitted when all threads are active, they will wait in the queue until a thread is available. If any thread 
terminates due to a failure during execution prior to shutdown, a new one will take its place if needed to execute 
subsequent tasks. The threads in the pool will exist until it is explicitly shutdown.
* __newWorkStealingPool(parallelism: Int): ExecutorService__ => Creates a thread pool that maintains enough threads to support
the given parallelism level, and may use multiple queues to reduce contention. The parallelism level corresponds to the
maximum number of threads actively engaged in, or available to engage in, task processing. The actual number of threads 
may grow and shrink dynamically. A work-stealing pool makes no guarantees about the order in which submitted tasks are executed.
* __newWorkStealingPool(): ExecutorService__ => Creates a work-stealing thread pool using all available processors as its target 
parallelism level.
* __newSingleThreadExecutor(): ExecutorService__ => Creates an Executor that uses a single worker thread operating
off an unbounded queue. (Note however that if this single thread terminates due to a failure during execution prior to
shutdown, a new one will take its place if needed to execute subsequent tasks.)  Tasks are guaranteed to execute
sequentially, and no more than one task will be active at any given time. Unlike the otherwise equivalent ''newFixedThreadPool(1)'' 
the returned executor is guaranteed not to be reconfigurable to use additional threads.
* __newCachedThreadPool(): ExecutorService__ =>  Creates a thread pool that creates new threads as needed, but will reuse 
previously constructed threads when they are available. These pools will typically improve the performance
of programs that execute many short-lived asynchronous tasks. Calls to `execute` will reuse previously constructed
threads if available. If no existing thread is available, a new thread will be created and added to the pool. Threads that have
not been used for sixty seconds are terminated and removed from the cache. Thus, a pool that remains idle for long enough will
not consume any resources. Note that pools with similar properties but different details (for example, timeout parameters)
may be created using constructors.   
   
## The Scala Global ExecutionContext
[Jessica K - The global ExecutionContext makes your life easier](http://blog.jessitron.com/2014/02/scala-global-executioncontext-makes.html) does
a great job at explaining the `scala.concurrent.ExecutionContext.global`, let's listen to her:

When I try to put some code in a Future without specifying where to run it, Scala tells me what to do:

    scala> Future(println("Do something slow"))
    <console>:14: error: Cannot find an implicit ExecutionContext, either require one yourself or import ExecutionContext.Implicits.global
      
The global ExecutionContext has an objective of keeping your CPU busy while limiting time spent context switching between threads. To do this, 
it starts up a ForkJoinPool whose desired degree of parallelism is the number of CPUs.

ForkJoinPool is particularly smart, able to run small computations with less overhead. It's more work for its users, who must implement each 
computation inside a ForkJoinTask. Scala's global ExecutionContext takes this burden from you: any task submitted to the global context from 
within the global context is quietly wrapped in a ForkJoinTask.

But wait, there's more! We also get special handling for blocking tasks. Scala's Futures resist supplying their values unless you pass them to 
Await.result(). That's because Future knows that its result may not be available yet, so this is a blocking call. The Await object wraps the 
access in `scala.concurrent.blocking { ... }`, which passes the code on to BlockingContext.

The BlockingContext object says, "Hey, current Thread, do you have anything special to do before I start this slow thing?" and the special 
thread created inside the global ExecutionContext says, "Why yes! I'm going to tell the ForkJoinPool about this."

The thread's block context defers to the managedBlock method in ForkJoinPool, which activates the ForkJoinPool's powers of compensation. 
ForkJoinPool is trying to keep the CPU busy by keeping degree-of-parallelism threads computing all the time. When informed that one of those 
threads is about to block, it compensates by starting an additional thread. This way, while your thread is sitting around, a CPU doesn't have to. 
As a bonus, this prevents pool-induced deadlock.

In this way, Scala's Futures and its global ExecutionContext work together to keep your computer humming without going Thread-wild. You can 
invoke the same magic yourself by wrapping any Thread-hanging code in [blocking { ... }](https://gist.github.com/jessitron/8777503).

All this makes scala.concurrent.ExecutionContext.global an excellent general-purpose ExecutionContext.

When should you not use it? When you're writing an asynchronous library, or when you know you're going to do a lot of blocking, declare your own 
thread pool. Leave the global one for everyone else.
   
## Choosing An ExecutionService to Wrap
According to [Jessica K - Choosing an ExecutorService](http://blog.jessitron.com/2014/01/choosing-executorservice.html), 
There are two reasons to do multi threading: parallel computing or avoid blocking on I/O (i.e. writing to a file/database),
and it boils down to:
        
* Parallel computation: 
** When you have simple parallel computations, that themselves start other computations, then use a ForkJoinPool (WorkStealingPool)
** When you have simple parallel computations use a FixedThreadPool with as many threads as CPUs        

* Not waiting on I/O:
** When you need to limit the number of threads that can wait on I/O, use a FixedThreadPool
** When you don't want to limit the number of threads that can wait on I/O, use a cacheThreadPool

__Note:__ Blocking I/O operations (JDBC), number crunching code that by virtue of calculation uses the thread, 
because there are only a small number of threads, this can lead to thread starvation, which means there are no more
threads available and no more tasks can be executed, halting the system.

# Akka Dispatchers
An [Akka MessageDispatcher](http://doc.akka.io/docs/akka/2.3.6/scala/dispatchers.html) is what makes Akka Actors "tick", 
it is the engine of the machine so to speak. __All MessageDispatcher implementations are also an ExecutionContext__, which means 
that they can be used to execute arbitrary code, for instance Futures, Spray (Un)Marshallers, Scheduler, etc.                 

## The Default Dispatcher
Every ActorSystem will have a default dispatcher that will be used in case nothing else is configured for an Actor. The 
default dispatcher can be configured, and is by default a Dispatcher with the specified default-executor. If an 
ActorSystem is created with an ExecutionContext passed in, this ExecutionContext will be used as the default executor 
for all dispatchers in this ActorSystem. If no ExecutionContext is given, it will fallback to the executor specified 
in akka.actor.default-dispatcher.default-executor.fallback. By default this is a "fork-join-executor", which gives 
excellent performance in most cases.

## Looking up a Dispatcher
Dispatchers implement the ExecutionContext interface and can thus be used to run Future invocations etc.

    // for use with Futures, Scheduler, etc.
    implicit val executionContext = system.dispatchers.lookup("my-dispatcher")
        
## Setting the dispatcher for an Actor
So in case you want to give your Actor a different dispatcher than the default, you need to do two things, of which 
the first is to configure the dispatcher in the root of the application.conf:
   
    my-dispatcher {
     # 'my-dispatcher' is the name of the event-based dispatcher
     type = Dispatcher
     # What kind of ExecutionService to use
     executor = "fork-join-executor"
     # Configuration for the fork join pool
     fork-join-executor {
       # Min number of threads to cap factor-based parallelism number to
       parallelism-min = 2
       # Parallelism (threads) ... ceil(available processors * factor), core i7 with HT = 8 CPUs * 2 = 16 threads
       parallelism-factor = 2.0
       # Max number of threads to cap factor-based parallelism number to
       parallelism-max = 10
     }
     # Throughput defines the maximum number of messages to be
     # processed per actor before the thread jumps to the next actor.
     # Set to 1 for as fair as possible.
     throughput = 100
    }

And here's another example that uses the "thread-pool-executor":

    my-thread-pool-dispatcher {
      # 'my-thread-pool-dispatcher' is the name of the event-based dispatcher
      type = Dispatcher
      # What kind of ExecutionService to use
      executor = "thread-pool-executor"
      # Configuration for the thread pool
      thread-pool-executor {
        # minimum number of threads to cap factor-based core number to
        core-pool-size-min = 2
        # No of core threads ... ceil(available processors * factor)
        core-pool-size-factor = 2.0
        # maximum number of threads to cap factor-based number to
        core-pool-size-max = 10
      }
      # Throughput defines the maximum number of messages to be
      # processed per actor before the thread jumps to the next actor.
      # Set to 1 for as fair as possible.
      throughput = 100
    }

Then you create the actor as usual and define the dispatcher in the deployment configuration.

    import akka.actor.Props
    val myActor = context.actorOf(Props[MyActor], "myactor")

application.conf (you don't need to put /usr in):

    akka.actor.deployment {
      /myactor {
        dispatcher = my-dispatcher
      }
    }        

An alternative to the deployment configuration is to define the dispatcher in code. If you define the dispatcher 
in the deployment configuration then this value will be used instead of programmatically provided parameter.

    import akka.actor.Props
    val myActor = context.actorOf(Props[MyActor].withDispatcher("my-dispatcher"), "myactor1")
            
## Types of dispatchers
There are 4 different types of message dispatchers:

* __Dispatcher:__ This is an event-based dispatcher that binds a set of Actors to a thread pool. It is the default 
dispatcher used if one is not specified.
** Sharability: Unlimited
** Mailboxes: Any, creates one per Actor
** Use cases: Default dispatcher, Bulkheading
** Driven by: java.util.concurrent.ExecutorService
** Config: specify using "executor" using "fork-join-executor", "thread-pool-executor" or the FQCN of an akka.dispatcher.ExecutorServiceConfigurator    
* __PinnedDispatcher:__ This dispatcher dedicates a unique thread for each actor using it; i.e. each actor will have 
its own thread pool with only one thread in the pool.
** Sharability: None
** Mailboxes: Any, creates one per Actor
** Use cases: Bulkheading
** Driven by: Any akka.dispatch.ThreadPoolExecutorConfigurator, by default a "thread-pool-executor"
* __BalancingDispatcher:__ This is an executor based event driven dispatcher that will try to redistribute work 
from busy actors to idle actors. All the actors share a single Mailbox that they get their messages from. It is 
assumed that all actors using the same instance of this dispatcher can process all messages that have been sent to 
one of the actors; i.e. the actors belong to a pool of actors, and to the client there is no guarantee about which 
actor instance actually processes a given message. 
** Sharability: Actors of the same type only   
** Mailboxes: Any, creates one for all Actors    
** Use cases: Work-sharing    
** Driven by: java.util.concurrent.ExecutorService
** Config: specify using "executor" using "fork-join-executor", "thread-pool-executor" or the FQCN of an akka.dispatcher.ExecutorServiceConfigurator 
Note that you can not use a BalancingDispatcher as a Router Dispatcher. (You can however use it for the Routees)
* __CallingThreadDispatcher:__ This dispatcher runs invocations on the current thread only. This dispatcher does not 
create any new threads, but it can be used from different threads concurrently for the same actor. 
See CallingThreadDispatcher for details and restrictions.
** Sharability: Unlimited
** Mailboxes: Any, creates one per Actor per Thread (on demand)
** Use cases: Testing
** Driven by: The calling thread (duh)

## Effective Akka
While the Future API is very handy for defining work to take place asynchronously, they require an ExecutionContext in 
order to perform their tasks. This ExecutionContext provides a thread pool from which they draw their required resources. 
Many people start off by using the ActorSystem default dispatcher like so:

    val system = ActorSystem()
    implicit val ec: ExecutionContext = system.dispatcher
    Future { /* work to be performed */ }

However, using the ActorSystem’s default dispatcher can lead to thread starvation very quickly if it becomes overloaded with 
potential work. The default configuration of this dispatcher is to be elastically sized from 8 to 64 threads. 

    akka.actor.default-dispatcher {
        type = "dispatcher"
        executor = "default-executor"
        default-executor {
          fallback = "fork-join-executor"
        }
        fork-join-executor {
            parallelism-min = 8
            parallelism-factor = 3.0
            parallelism-max = 64
        }
        throughput = 5
    }

We should consider it important to isolate execution by context. This gives you more resource granularity but still requires 
your actor to dedicate a thread to the future every time one is instantiated.

This also might not be ideal:

    implicit val ec: ExecutionContext = context.dispatcher
    Future { /* work to be performed */ }

You always have the option of creating a new ExecutionContext from a new thread pool on the fly, which can be done like so:

    implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(new ForkJoinPool())
    Future { /* work to be performed */ }

However, a best practice I recommend is that you consider when you may want to define specific dispatchers inside the 
configuration of each ActorSystem in which futures will be used. Then you can dynamically apply the dispatcher for use 
in your code, like this:

    implicit val ec: ExecutionContext = context.system.dispatchers.lookup("foo")
    Future { /* work to be performed */ }            

## Available ExecutorServices in Akka Configuration
The following ExecutorServices are available by default, but you can configure your own:

* executor: "default-executor"
* executor: "fork-join-executor": 
* executor: "thread-pool-executor"

## Custom ExecutionService (ThreadPool) Configuration
In certain circumstances, you may wish to dispatch work to other thread pools. This may include CPU heavy work, or IO work, 
such as database access. To do this, you should first create a thread pool, this can be done easily in Scala:

    implicit val ec: ExecutionContext = system.dispatchers.lookup("my-context")

In application.conf, you can add a section to the root of the configuration to create an ExecutionService:

    my-context {
      fork-join-executor {
        parallelism-factor = 20.0
        parallelism-max = 200
      }
    }
    
## Play Framework Execution Services Best Practise (Profiles)
* __Purely Asynchronous:__ you are doing no blocking IO in your application. Since you are never blocking, the default 
configuration of one thread per processor suits your use case perfectly, so no extra configuration needs to be done. Note
that Akka uses a multiplier factor of 3, with a maximum number of worker threads of 64, so using a i7, 4 core with HT giving
the OS effectively 8 cpus, we have 8 * 3 = 24 worker threads. But the default ForkJoin pool may actively grow or shrink to 
the given min/max caps which is 8 .. 64 worker threads.
* __Highly synchronous:__ This profile matches that of a traditional synchronous IO based web framework, such as a Java 
servlet container. It uses large thread pools to handle blocking IO. It is useful for applications where most actions are 
doing database synchronous IO calls, such as accessing a database, and you don’t want or need control over concurrency 
for different types of work. This profile is the simplest for handling blocking IO. In this profile, you would simply use 
the default execution context everywhere, but configure it to have ''a very large number of threads'' in its pool, like so:

```
akka {
  actor {
    default-dispatcher = {
      fork-join-executor {
        parallelism-min = 300
        parallelism-max = 300
      }
    }
  }
}
```

## Examples
           
