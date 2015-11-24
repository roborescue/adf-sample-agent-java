package adf.sample.ambulance.tactics;

import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.platoon.action.Action;
import adf.agent.platoon.action.ambulance.ActionLoad;
import adf.agent.platoon.action.ambulance.ActionRescue;
import adf.agent.platoon.action.ambulance.ActionUnload;
import adf.agent.platoon.action.common.ActionMove;
import adf.agent.platoon.extaction.ExtAction;
import adf.algorithm.path.PathPlanner;
import adf.algorithm.path.SamplePathPlanner;
import adf.sample.ambulance.tactics.extaction.ActionTransport;
import adf.tactics.TacticsAmbulance;
import adf.util.datastorage.DataStorage;
import rescuecore2.log.Logger;
import rescuecore2.misc.collections.LazyMap;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.Entity;
import rescuecore2.worldmodel.EntityID;
import sample.DistanceSorter;

import java.util.*;

/**
 * Created by takamin on 10/14/15.
 */
public class MyTacticsAmbulance extends TacticsAmbulance
{
    private static final int RANDOM_WALK_LENGTH = 50;

    private Collection<EntityID> unexploredBuildings;

    protected List<EntityID> buildingIDs;
    protected List<EntityID> roadIDs;
    protected List<EntityID> refugeIDs;

    private Map<EntityID, Set<EntityID>> neighbours;
    private Random random;

    private PathPlanner pathPlanner;

    @Override
	public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
        this.random = new Random();
        buildingIDs = new ArrayList<>();
        roadIDs = new ArrayList<>();
        refugeIDs = new ArrayList<>();
	}

	@Override
	public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, DataStorage dataStorage)
	{
		//precpmpute
	}

	@Override
	public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, DataStorage dataStorage)
	{
		//load precompute data
        preparate(agentInfo, worldInfo, scenarioInfo);
	}

	@Override
	public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo) {
		//non precompute

        for (StandardEntity next : worldInfo.world) {
            if (next instanceof Building) {
                buildingIDs.add(next.getID());
            }
            if (next instanceof Road) {
                roadIDs.add(next.getID());
            }
            if (next instanceof Refuge) {
                refugeIDs.add(next.getID());
            }
        }
        //pathplanner
        //neighbours = search.getGraph();
        this.init(worldInfo);
        unexploredBuildings = new HashSet<>(buildingIDs);
        this.pathPlanner = new SamplePathPlanner(worldInfo, agentInfo, scenarioInfo);

	}

    private void init(WorldInfo worldInfo) {
        Map<EntityID, Set<EntityID>> neighbours = new LazyMap<EntityID, Set<EntityID>>() {
            @Override
            public Set<EntityID> createValue() {
            return new HashSet<>();
        }
        };
        Set<EntityID> buildingSet= new HashSet<>();
        for (Entity next : worldInfo.world) {
            if (next instanceof Area) {
                Collection<EntityID> areaNeighbours = ((Area) next).getNeighbours();
                neighbours.get(next.getID()).addAll(areaNeighbours);
                if (next instanceof Building)
                    buildingSet.add(next.getID());
            }
        }
        this.neighbours = neighbours;
    }

    protected List<EntityID> randomWalk(AgentInfo agentInfo) {
        List<EntityID> result = new ArrayList<>(RANDOM_WALK_LENGTH);
        Set<EntityID> seen = new HashSet<>();
        EntityID current = agentInfo.getPosition();
        for (int i = 0; i < RANDOM_WALK_LENGTH; ++i) {
            result.add(current);
            seen.add(current);
            List<EntityID> possible = new ArrayList<>(neighbours.get(current));
            Collections.shuffle(possible, random);
            boolean found = false;
            for (EntityID next : possible) {
                if (seen.contains(next)) {
                    continue;
                }
                current = next;
                found = true;
                break;
            }
            if (!found) {
                // We reached a dead-end.
                break;
            }
        }
        return result;
    }

	@Override
	public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo)
	{
        updateUnexploredBuildings(agentInfo.changed);//changed
        // Am I transporting a civilian to a refuge?
        if (someoneOnBoard(worldInfo, agentInfo)) {
            // Am I at a refuge?
            if (worldInfo.world.getEntity(agentInfo.getPosition()) instanceof Refuge) {
                // Unload!
                return new ActionUnload();
            }
            else {
                // Move to a refuge
                this.pathPlanner.setFrom(agentInfo.getPosition());
                this.pathPlanner.setDist(refugeIDs);
                List<EntityID> path = this.pathPlanner.getResult();
                if (path != null) {
                    return new ActionMove(path);
                }
                // What do I do now? Might as well carry on and see if we can dig someone else out.
                //Logger.debug("Failed to plan path to refuge");
            }
        }
        // Go through targets (sorted by distance) and check for things we can do
        for (Human next : getTargets(worldInfo, agentInfo)) {
            //ExtAction
            ActionTransport actionTransport = new ActionTransport(next, worldInfo, agentInfo, this.pathPlanner);
            actionTransport.calc();
            return actionTransport.getAction();
            //return actionTransport.calc().getAction();
        }
        // Nothing to do
        //List<EntityID> path = search.breadthFirstSearch(me().getPosition(), unexploredBuildings);
        this.pathPlanner.setFrom(agentInfo.getPosition());
        ((SamplePathPlanner)this.pathPlanner).setDist(unexploredBuildings);
        List<EntityID> path = this.pathPlanner.getResult();
        if (path != null) {
            //Logger.info("Searching buildings");
            //sendMove(time, path);
            return new ActionMove(path);
        }
        Logger.info("Moving randomly");
        return new ActionMove(randomWalk(agentInfo));
	}

    private boolean someoneOnBoard(WorldInfo worldInfo, AgentInfo agentInfo) {
        for (StandardEntity next : worldInfo.world.getEntitiesOfType(StandardEntityURN.CIVILIAN)) {
            if (((Human)next).getPosition().equals(agentInfo.getID())) {
                Logger.debug(next + " is on board");
                return true;
            }
        }
        return false;
    }

    private List<Human> getTargets(WorldInfo worldInfo, AgentInfo agentInfo) {
        List<Human> targets = new ArrayList<>();
        for (StandardEntity next : worldInfo.world.getEntitiesOfType(
                StandardEntityURN.CIVILIAN,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_FORCE,
                StandardEntityURN.AMBULANCE_TEAM)
                ) {
            Human h = (Human)next;
            if ((Human) worldInfo.world.getEntity(agentInfo.getID()) == h) {
                continue;
            }
            if (h.isHPDefined()
                    && h.isBuriednessDefined()
                    && h.isDamageDefined()
                    && h.isPositionDefined()
                    && h.getHP() > 0
                    && (h.getBuriedness() > 0 || h.getDamage() > 0)) {
                targets.add(h);
            }
        }
        Collections.sort(targets, new DistanceSorter(worldInfo.world.getEntity(agentInfo.getPosition()), worldInfo.world));
        return targets;
    }

    private void updateUnexploredBuildings(ChangeSet changed) {
        for (EntityID next : changed.getChangedEntities()) {
            unexploredBuildings.remove(next);
        }
    }
}
