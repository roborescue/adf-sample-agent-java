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

    // key is road ID, value is priority
    private Map<EntityID, Integer> tasks;
    // a Set that holds the ID of roads that have been cleared
    private Set<EntityID> completedTasks;
    // a Set that holds all entity IDs within the assigned area (cluster)
    private Set<EntityID> myCluster;
    // ID of the road to be cleared
    private EntityID result;

    // constructor
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
        // be called only once
        if (this.getCountPrecompute() > 1) { return this; }

        // get the entity ID of your own area
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

    // information update
    @Override
    public RoadDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        logger.debug("Time:" + agentInfo.getTime());

        // update tasks based on information
        updateTasksWithPerception();
        addClusterRoadsToTasksAsNeeded();

        return this;
    }

    // calculate clearing target
    @Override
    public RoadDetector calc() {
      // reset result
      this.result = null;
      EntityID bestTarget = null;
      int minPriority = Integer.MAX_VALUE;
      int minDistance = Integer.MAX_VALUE;

      for (Map.Entry<EntityID, Integer> task : this.tasks.entrySet()) {
        EntityID currentTarget = task.getKey();
        int currentPriority = task.getValue();

        // ignore if already cleared
        if (this.completedTasks.contains(currentTarget)) {
          continue;
        }

        // compare priority
        if (currentPriority < minPriority) {
          minPriority = currentPriority;
          bestTarget = currentTarget;
          minDistance = this.worldInfo.getDistance(this.agentInfo.getID(), bestTarget);
        } else if (currentPriority == minPriority) {
          // if priority is the same, compare distance
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

    // update tasks based on perception
    private void updateTasksWithPerception() {
        // loop through changed entities
        for (EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(id);
            // if the entity is a road
            if (entity instanceof Road) {
                Road road = (Road) entity;
                // if there are blockades on the road
                if (road.isBlockadesDefined() && !road.getBlockades().isEmpty()) {
                    // add to tasks with priority 1
                    this.tasks.put(road.getID(), 1);
                    // remove from completed tasks if it was there
                    this.completedTasks.remove(road.getID());
                } else {
                    // add to completed tasks
                    this.completedTasks.add(road.getID());
                }
            }
        }
    }


    private void addClusterRoadsToTasksAsNeeded() {
        if(this.myCluster == null) return;
        for(EntityID id : this.myCluster) {
            // if road entity
            if(this.worldInfo.getEntity(id) instanceof Road) {
                // add to tasks with priority 8
                this.tasks.putIfAbsent(id, 8);
            }
        }
    }

    // return the ID of the road to be cleared
    @Override
    public EntityID getTarget() {
        return this.result;
    }
}