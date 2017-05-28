package test_team.module.complex.self;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_TEAM;
import static rescuecore2.standard.entities.StandardEntityURN.CIVILIAN;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;

import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.complex.HumanDetector;
import rescuecore2.standard.entities.Civilian;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;
import test_team.Utils.TestLogger;
import test_team.Utils.TestUtils;

public class TestHumanDetector extends HumanDetector {
	private Clustering clustering;

	private EntityID result;

	private Logger logger;

	public TestHumanDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		logger = TestLogger.getLogger(agentInfo.me());
		this.clustering = moduleManager.getModule("TestHumanDetector.Clustering", "adf.sample.module.algorithm.SampleKMeans");
		registerModule(this.clustering);
	}

	@Override
	public HumanDetector updateInfo(MessageManager messageManager) {
		logger.debug("Time:"+agentInfo.getTime());
		super.updateInfo(messageManager);
		return this;
	}

	@Override
	public HumanDetector calc() {
		Human transportHuman = this.agentInfo.someoneOnBoard();
		if (transportHuman != null) {
			logger.debug("someoneOnBoard:" + transportHuman);
			this.result = transportHuman.getID();
			return this;
		}
		if (this.result != null) {
			Human target = (Human) this.worldInfo.getEntity(this.result);
			if (!isValidHuman(target)) {
				logger.debug("Invalid Human:" + target + " ==>reset target");
				this.result = null;
			}
		}
		if (this.result == null) {
			this.result = calcTarget();
		}
		return this;
	}

	private EntityID calcTarget() {
		List<Human> rescueTargets = filterRescueTargets(this.worldInfo.getEntitiesOfType(CIVILIAN));
		List<Human> rescueTargetsInCluster = filterInCluster(rescueTargets);
		List<Human> targets = rescueTargetsInCluster;
		if (targets.isEmpty())
			targets = rescueTargets;

		
		logger.debug("Targets:"+targets);
		if (!targets.isEmpty()) {
			targets.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
			Human selected = targets.get(0);
			logger.debug("Selected:"+selected);
			return selected.getID();
		}
		
		return null;
	}

	@Override
	public EntityID getTarget() {
		return this.result;
	}

	public boolean isValidHuman(StandardEntity entity) {
		return TestUtils.isValidHuman(worldInfo, entity);
	}

	public List<Human> filterRescueTargets(Collection<? extends StandardEntity> list) {
		List<Human> rescueTargets = new ArrayList<>();
		for (StandardEntity next : list) {
			if (!(next instanceof Human))
				continue;
			Human h = (Human) next;
			if (!isValidHuman(h))
				continue;
			if (h.getBuriedness() == 0)
				continue;
			rescueTargets.add(h);
		}
		return rescueTargets;
	}

	private List<Human> filterInCluster(Collection<? extends StandardEntity> entities) {
		int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
		List<Human> filter = new ArrayList<>();
		HashSet<StandardEntity> inCluster = new HashSet<>(clustering.getClusterEntities(clusterIndex));
		for (StandardEntity next : entities) {
			if (!(next instanceof Human))
				continue;
			Human h = (Human) next;
			if (!h.isPositionDefined())
				continue;
			StandardEntity position = this.worldInfo.getPosition(h);
			if (position == null)
				continue;
			if (!inCluster.contains(position))
				continue;
			filter.add(h);
		}
		return filter;

	}

	private Civilian selectCivilian(Civilian civilian1, Civilian civilian2) {
		if (civilian1.getHP() > civilian2.getHP()) {
			return civilian1;
		} else if (civilian1.getHP() < civilian2.getHP()) {
			return civilian2;
		} else {
			if (civilian1.getBuriedness() > 0 && civilian2.getBuriedness() == 0) {
				return civilian1;
			} else if (civilian1.getBuriedness() == 0 && civilian2.getBuriedness() > 0) {
				return civilian2;
			} else {
				if (civilian1.getBuriedness() < civilian2.getBuriedness()) {
					return civilian1;
				} else if (civilian1.getBuriedness() > civilian2.getBuriedness()) {
					return civilian2;
				}
			}
			if (civilian1.getDamage() < civilian2.getDamage()) {
				return civilian1;
			} else if (civilian1.getDamage() > civilian2.getDamage()) {
				return civilian2;
			}
		}
		return civilian1;
	}

	private class DistanceSorter implements Comparator<StandardEntity> {
		private StandardEntity reference;
		private WorldInfo worldInfo;

		DistanceSorter(WorldInfo wi, StandardEntity reference) {
			this.reference = reference;
			this.worldInfo = wi;
		}

		public int compare(StandardEntity a, StandardEntity b) {
			int d1 = this.worldInfo.getDistance(this.reference, a);
			int d2 = this.worldInfo.getDistance(this.reference, b);
			return d1 - d2;
		}
	}

}
