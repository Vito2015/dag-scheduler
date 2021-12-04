package com.dvbug.dag;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static java.lang.Thread.sleep;

/**
 * DAG 图节点
 *
 * @param <T> {@link NodeBean}子类
 */
@Getter
@Slf4j
public class DagNode<T extends NodeBean> implements Executor {

    private final DagNodeInfo info;
    private final TraceInfo trace;
    private final T bean;
    private DagNodeState state;
    @Setter(AccessLevel.MODULE)
    private int expectDependCount;
    @Setter
    private DagNodeStateChange stateChange;
    @Getter(AccessLevel.MODULE)
    private Throwable nodeThrowable;

    public DagNode(T bean) {
        this(bean, -1);
    }

    public DagNode(T bean, long timeout) {
        this.bean = bean;

        this.info = new DagNodeInfo(
                String.format("%s-%s", System.currentTimeMillis(), bean.hashCode()),
                String.format("node-%s", bean.getName()),
                timeout);
        this.trace = new TraceInfo(System.currentTimeMillis());
        this.state = DagNodeState.CREATED;
    }

    public void putParam(Object param) {
        bean.setParam(param);
        printParamsCount();
    }

    private void printParamsCount() {
        log.debug("{}, param depend expect={}, actual={}", this, expectDependCount, bean.getParamCount());
    }

    private void setState(DagNodeState state) {
        if (this.state.equals(state)) {
            return;
        }
        DagNodeState oldState = this.state;
        if (!DagNodeStateTransition.transAllow(oldState, state)) {
            return;
        }
        this.state = state;
        this.getTrace().setFinalState(state);
        if (null != stateChange) {
            stateChange.changed(oldState, state, this.info);
        }
    }

    @Override
    public boolean execute(ExecuteCallback callback) {
        log.debug("{} is start", this);

        setState(DagNodeState.START);

        long expired = 0;
        while ((state != DagNodeState.RUNNING && state != DagNodeState.INEFFECTIVE) && (-1 == info.getTimeout() || expired <= info.getTimeout())) {
            printParamsCount();
            if (bean.getParamCount() >= expectDependCount && bean.executeAble()) {
                setState(DagNodeState.RUNNING);
                break;
            } else {
                try {
                    setState(DagNodeState.WAITING);
                    sleep(1);
                } catch (InterruptedException e) {
                    setState(DagNodeState.FAILED);
                    e.printStackTrace();
                    nodeThrowable = e;
                    callback.onCompleted(new ExecuteResult<>(this.info, e));
                    return false;
                }
            }
            expired = System.currentTimeMillis() - getTrace().getStartedTime();
        }

        boolean nodeExecuteOk;
        if (this.state == DagNodeState.RUNNING) {
            boolean succeed = bean.execute();
            setState(succeed ? DagNodeState.SUCCESS : DagNodeState.FAILED);
            if (succeed) {
                callback.onCompleted(new ExecuteResult<>(this.info, bean.getResult()));
            } else {
                callback.onCompleted(new ExecuteResult<>(this.info, bean.getThrowable()));
            }
            nodeExecuteOk = true;
        } else if (this.state == DagNodeState.INEFFECTIVE) {
            callback.onCompleted(new ExecuteResult<>(this.info,
                    new IllegalStateException(String.format("%s node ineffective", info.getName()))));
            nodeExecuteOk = true;
        } else {
            nodeThrowable = new IllegalStateException(String.format("%s invalid state %s", info.getName(), state));
            callback.onCompleted(new ExecuteResult<>(this.info, nodeThrowable));
            nodeExecuteOk = false;
        }

        return nodeExecuteOk;
    }

    public boolean isRunning() {
        return state == DagNodeState.RUNNING;
    }

    public boolean isScheduled() {
        return state != DagNodeState.CREATED;
    }

    public boolean isFinished() {
        return state == DagNodeState.SUCCESS || state == DagNodeState.FAILED;
    }

    void setPrepared() {
        setState(DagNodeState.PREPARED);
    }

    void setIneffective() {
        setState(DagNodeState.INEFFECTIVE);
    }

    @Override
    public String toString() {
        return String.format("%s[%s, %s]", this.getClass().getSimpleName(), info.getName(), state);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DagNode<?> dagNode = (DagNode<?>) o;
        return info.equals(dagNode.info) && bean.equals(dagNode.bean);
    }

    @Override
    public int hashCode() {
        return Objects.hash(info, bean);
    }
}

