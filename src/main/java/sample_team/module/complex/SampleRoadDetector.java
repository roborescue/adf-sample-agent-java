package sample_team.module.complex;

import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.algorithm.PathPlanning;
import adf.core.component.module.complex.RoadDetector;
import adf.core.debug.DefaultLogger;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;
import adf.core.agent.communication.MessageManager;

public class SampleRoadDetector extends RoadDetector {

    private PathPlanning pathPlanning;
    private Clustering clustering;
    private Logger logger;

    // keyが道路ID，valueが優先度
    private Map<EntityID, Integer> tasks;
    // 啓開が完了した道路のIDを保持するSet
    private Set<EntityID> completedTasks;
    // 自身に割り当てられた担当区画（クラスタ）内の全エンティティIDを保持するSet
    private Set<EntityID> myCluster;
    // 啓開対象となる道路のID
    private EntityID result;

    // コンストラクタ
    public SampleRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);

        this.logger = DefaultLogger.getLogger(agentInfo.me());
        this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.impl.module.algorithm.DijkstraPathPlanning");
        this.clustering = moduleManager.getModule("SampleRoadDetector.Clustering", "adf.impl.module.algorithm.KMeansClustering");

        registerModule(this.clustering);
        registerModule(this.pathPlanning);

        this.tasks = new HashMap<>();
        this.completedTasks = new HashSet<>();
        this.myCluster = new HashSet<>();
    }


    @Override
    public RoadDetector preparate() {
        super.preparate();
        // 1回だけ呼ばれるようにする
        if (this.getCountPrecompute() > 1) { return this; }

        // 自身の担当区画のエンティティIDを取得
        int myClusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
        if (myClusterIndex != -1) {
            Collection<StandardEntity> clusterEntities = this.clustering.getClusterEntities(myClusterIndex);
            if(clusterEntities != null) {
                this.myCluster.addAll(
                    clusterEntities.stream().map(StandardEntity::getID).collect(Collectors.toSet())
                );
            }
        }
        return this;
    }

    // 情報更新
    @Override
    public RoadDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        logger.debug("Time:" + agentInfo.getTime());

        // 情報をもとにタスクを更新
        updateTasksWithPerception();
        addClusterRoadsToTasksAsNeeded();

        return this;
    }

    // 啓開目標を計算
    @Override
    public RoadDetector calc() {
      // 目標をリセット
      this.result = null;
      EntityID bestTarget = null;
      int minPriority = Integer.MAX_VALUE; // 見つけたタスクの最小優先度を記録 (低いほど優先)
      int minDistance = Integer.MAX_VALUE; // 優先度が同じ場合の最短距離を記録

      // --- 全てのタスクをループして最適なものを探す ---
      for (Map.Entry<EntityID, Integer> task : this.tasks.entrySet()) {
        EntityID currentTarget = task.getKey();
        int currentPriority = task.getValue();

        // 啓開済みの道路であれば無視
        if (this.completedTasks.contains(currentTarget)) {
          continue;
        }

        // 優先度を比較
        if (currentPriority < minPriority) {
          minPriority = currentPriority;
          bestTarget = currentTarget;
          minDistance = this.worldInfo.getDistance(this.agentInfo.getID(), bestTarget);
        } else if (currentPriority == minPriority) {
          // 優先度が同じ場合は，距離を比較
          int currentDistance = this.worldInfo.getDistance(this.agentInfo.getID(), currentTarget);
          if (currentDistance < minDistance) {
            bestTarget = currentTarget;
            minDistance = currentDistance;
          }
        }
      }

      if (bestTarget != null) {
        this.result = bestTarget;
      } else {
        if(!this.completedTasks.isEmpty()) {
          this.completedTasks.clear();
        }
      }
      return this;
    }

    // タスクリストの更新
    private void updateTasksWithPerception() {
        // 視界に入って情報が変化したエンティティをループ
        for (EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(id);
            // 道路である場合
            if (entity instanceof Road) {
                Road road = (Road) entity;
                // 道路に瓦礫が存在する場合
                if (road.isBlockadesDefined() && !road.getBlockades().isEmpty()) {
                    // タスクリストに追加（優先度は1）
                    this.tasks.put(road.getID(), 1);
                    // 啓開済みになっている場合は，リストから削除
                    this.completedTasks.remove(road.getID());
                } else {
                    // 瓦礫がない場合は，啓開済みリストに追加
                    this.completedTasks.add(road.getID());
                }
            }
        }
    }


    private void addClusterRoadsToTasksAsNeeded() {
        if(this.myCluster == null) return;
        for(EntityID id : this.myCluster) {
            // 道路エンティティの場合
            if(this.worldInfo.getEntity(id) instanceof Road) {
                // タスクリストに追加（優先度は8）
                this.tasks.putIfAbsent(id, 8);
            }
        }
    }

    //  啓開対象の道路IDを返す
    @Override
    public EntityID getTarget() {
        return this.result;
    }
}