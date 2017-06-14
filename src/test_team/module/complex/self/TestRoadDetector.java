package test_team.module.complex.self;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.FIRE_BRIGADE;
import static rescuecore2.standard.entities.StandardEntityURN.GAS_STATION;
import static rescuecore2.standard.entities.StandardEntityURN.POLICE_FORCE;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import adf.debug.TestLogger;
import org.apache.log4j.Logger;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class TestRoadDetector extends RoadDetector {
	private Set<Area> openedAreas = new HashSet<>();
	private Clustering clustering;
	private PathPlanning pathPlanning;

	private EntityID result;
	private Logger logger;

	public TestRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.pathPlanning = moduleManager.getModule("TestRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
		this.clustering = moduleManager.getModule("TestRoadDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
		registerModule(this.clustering);
		registerModule(this.pathPlanning);
		this.result = null;

	}

	@Override
	public RoadDetector updateInfo(MessageManager messageManager) {
		logger.debug("Time:"+agentInfo.getTime());
		super.updateInfo(messageManager);
		return this;
	}
	
	@Override
	public RoadDetector calc() {
		EntityID positionID = this.agentInfo.getPosition();
		StandardEntity currentPosition = worldInfo.getEntity(positionID);
		openedAreas.add((Area) currentPosition);
		if (positionID.equals(result)) {
			logger.debug("reach to " + currentPosition + " reseting target");
			this.result = null;
		}

		if (this.result == null) {
			HashSet<Area> currentTargets = calcTargets();
			logger.debug("Targets: " + currentTargets);
			if (currentTargets.isEmpty()) {
				this.result = null;
				return this;
			}
			this.pathPlanning.setFrom(positionID);
			this.pathPlanning.setDestination(toEntityIds(currentTargets));
			List<EntityID> path = this.pathPlanning.calc().getResult();
			if (path != null && path.size() > 0) {
				this.result = path.get(path.size() - 1);
			}
			logger.debug("Selected Target: " + this.result);
		}
		return this;
	}

	private Collection<EntityID> toEntityIds(Collection<? extends StandardEntity> entities) {
		ArrayList<EntityID> eids = new ArrayList<>();
		for (StandardEntity standardEntity : entities) {
			eids.add(standardEntity.getID());
		}
		return eids;
	}

	private HashSet<Area> calcTargets() {
		HashSet<Area> targetAreas = new HashSet<>();
		for (StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, GAS_STATION)) {
			targetAreas.add((Area) e);
		}
		for (StandardEntity e : this.worldInfo.getEntitiesOfType(CIVILIAN, AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE)) {
			if (isValidHuman(e)) {
				Human h = (Human) e;
				targetAreas.add((Area) worldInfo.getEntity(h.getPosition()));
			}
		}
		HashSet<Area> inClusterTarget = filterInCluster(targetAreas);
		inClusterTarget.removeAll(openedAreas);
		return inClusterTarget;
	}

	private HashSet<Area> filterInCluster(HashSet<Area> targetAreas) {
		int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
		HashSet<Area> clusterTargets = new HashSet<>();
		HashSet<StandardEntity> inCluster = new HashSet<>(clustering.getClusterEntities(clusterIndex));
		for (Area target : targetAreas) {
			if (inCluster.contains(target))
				clusterTargets.add(target);

		}
		return clusterTargets;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}

	private boolean isValidHuman(StandardEntity entity) {
		if (entity == null)
			return false;
		if (!(entity instanceof Human))
			return false;

		Human target = (Human) entity;
		if (!target.isHPDefined() || target.getHP() == 0)
			return false;
		if (!target.isPositionDefined())
			return false;
		if (!target.isDamageDefined() || target.getDamage() == 0)
			return false;
		if (!target.isBuriednessDefined())
			return false;

		StandardEntity position = worldInfo.getPosition(target);
		if (position == null)
			return false;

		StandardEntityURN positionURN = position.getStandardURN();
		if (positionURN == REFUGE || positionURN == AMBULANCE_TEAM)
			return false;

		return true;
	}
}
