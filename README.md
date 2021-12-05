# dag-scheduler
基于有向无环图(DAG)的策略引擎

##
在策略引擎中，所有节点都有如下状态：
- CREATED `//节点已经创建`
- PREPARED `//准备开始调度`
- START `//开始调度`
- WAITING `//等待执行`
- RUNNING `//正在执行`
- SUCCESS `//执行成功`
- FAILED `//执行失败`
- INEFFECTIVE `//无效节点(父节点失败后下游单依赖子节点全部无效)`
- TIMEOUT `//执行超时`

所有节点都会被引擎调度到“PREPARED”状态，<br>
根节点首先被调度到“RUNNING”状态，其下游路径节点获得“WAITING”状态，<br>
根节点执行完毕后只有符合条件的下游节点会被调度到“RUNNING”状态进行逻辑执行；<br>
同层级不符合条件的会被转化为“FAILED”状态，<br>
对应下游所有单依赖节点都会被迫变成“INEFFECTIVE”状态（说明该路径永不可达）。<br>
获得执行的节点输出的结果会被引擎路由给该路径下游所有节点，下游节点顺势获得“RUNNING”状态，<br>
在其逻辑内部对上游传递过来的结果进行判断是否使用，然后继续输出结果给下游，<br>
直到DAG终节点结束得到最后结果。<br>

##
执行细节参考日志: [LOG.txt](./LOG.txt)