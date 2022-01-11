package sample_team.module.complex;

import adf.core.agent.communication.MessageManager;
import adf.core.agent.develop.DevelopData;
import adf.core.agent.info.AgentInfo;
import adf.core.agent.info.ScenarioInfo;
import adf.core.agent.info.WorldInfo;
import adf.core.agent.module.ModuleManager;
import adf.core.component.module.algorithm.Clustering;
import adf.core.component.module.complex.BuildingDetector;
import adf.core.debug.DefaultLogger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.apache.log4j.Logger;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleBuildingDetector extends BuildingDetector {

  private EntityID result;
  private Clustering clustering;
  private Logger logger;

  public SampleBuildingDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
    super(ai, wi, si, moduleManager, developData);
    logger = DefaultLogger.getLogger(agentInfo.me());
    this.clustering = moduleManager.getModule(
        "SampleBuildingDetector.Clustering",
        "adf.impl.module.algorithm.KMeansClustering");
    registerModule(this.clustering);
  }


  @Override
  public BuildingDetector updateInfo(MessageManager messageManager) {
    logger.debug("Time:" + agentInfo.getTime());
    super.updateInfo(messageManager);
    return this;
  }


  @Override
  public BuildingDetector calc() {
    this.result = this.calcTarget();
    return this;
  }


  private EntityID calcTarget() {
    Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
        StandardEntityURN.BUILDING, StandardEntityURN.GAS_STATION,
        StandardEntityURN.AMBULANCE_CENTRE, StandardEntityURN.FIRE_STATION,
        StandardEntityURN.POLICE_OFFICE);

    List<Building> fireyBuildings = filterFiery(entities);
    List<Building> clusterBuildings = filterInCluster(fireyBuildings);
    List<Building> targets = clusterBuildings;
    if (clusterBuildings.isEmpty())
      targets = fireyBuildings;
    logger.debug("FieryBuildingsTargets: " + targets);
    if (targets.isEmpty())
      return null;

    Collections.sort(targets, new DistanceSorter(worldInfo, agentInfo.me()));
    Building selectedBuilding = targets.get(0);
    logger.debug("Selected:" + selectedBuilding);
    return selectedBuilding.getID();
  }


  private List<Building>
      filterFiery(Collection<? extends StandardEntity> input) {
    ArrayList<Building> fireBuildings = new ArrayList<>();
    for (StandardEntity entity : input) {
      if (entity instanceof Building && ((Building) entity).isOnFire()) {
        fireBuildings.add((Building) entity);
      }
    }
    return fireBuildings;
  }


  private List<Building> filterInCluster(Collection<Building> targetAreas) {
    int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
    List<Building> clusterTargets = new ArrayList<>();
    HashSet<StandardEntity> inCluster = new HashSet<>(
        clustering.getClusterEntities(clusterIndex));
    for (Building target : targetAreas) {
      if (inCluster.contains(target))
        clusterTargets.add(target);
    }
    return clusterTargets;
  }


  @Override
  public EntityID getTarget() {
    return this.result;
  }

  private class DistanceSorter implements Comparator<Building> {

    private StandardEntity reference;
    private WorldInfo worldInfo;

    DistanceSorter(WorldInfo wi, StandardEntity reference) {
      this.reference = reference;
      this.worldInfo = wi;
    }


    public int compare(Building a, Building b) {
      if (a.getFieryness() == 3 && b.getFieryness() != 3) {
        return 1;
      }
      if (a.getFieryness() != 3 && b.getFieryness() == 3) {
        return -1;
      }
      int d1 = this.worldInfo.getDistance(this.reference, a);
      int d2 = this.worldInfo.getDistance(this.reference, b);
      return d1 - d2;

    }
  }
}