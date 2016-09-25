package adf.sample.module.complex;


import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandPolice;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.BuildingSelector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleBuildingSelector extends BuildingSelector {
    private int thresholdCompleted;
    private int thresholdRefill;
    private EntityID result;

    private EntityID oldPosition;
    private int oldX;
    private int oldY;
    private int sendTime;
    private int commandInterval;

    public SampleBuildingSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        int maxWater = scenarioInfo.getFireTankMaximum();
        int maxExtinguishPower = scenarioInfo.getFireExtinguishMaxSum();
        this.thresholdCompleted = (maxWater / 10) * developData.getInteger("fire.threshold.completed", 10);
        this.thresholdRefill = maxExtinguishPower * developData.getInteger("fire.threshold.refill", 1);
        this.result = null;
        this.oldPosition = ai.getID();
        this.oldX = -1;
        this.oldY = -1;
        this.sendTime = 0;
        this.commandInterval = developData.getInteger("fire.command.clear.interval", 5);
    }

    @Override
    public BuildingSelector updateInfo(MessageManager messageManager) {
        Human agent = (Human)this.agentInfo.me();
        EntityID agentPosition = agent.getPosition();
        if(agentPosition.getValue() == this.oldPosition.getValue()) {
            if (this.equalsPoint(agent.getX(), agent.getY(), this.oldX, this.oldY, 1000)) {
                int currentTime = this.agentInfo.getTime();
                if ((this.sendTime + this.commandInterval) <= currentTime) {
                    this.sendTime = currentTime;
                    messageManager.addMessage(
                            new CommandPolice(true, null, agentPosition, CommandPolice.ACTION_CLEAR)
                    );
                }
            }
        }
        this.oldPosition = agentPosition;
        this.oldX = agent.getX();
        this.oldY = agent.getY();
        return this;
    }

    @Override
    public BuildingSelector calc() {
        this.result = null;
        // refill
        this.result = this.calcRefill();
        if(this.result != null) {
            return this;
        }
        // select building
        Clustering clustering = this.moduleManager.getModule("TacticsFire.Clustering");
        if(clustering == null) {
            this.result = this.calcTargetInWorld();
            return this;
        }
        this.result = this.calcTargetInCluster(clustering);
        if(this.result == null) {
            this.result = this.calcTargetInWorld();
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private EntityID calcRefill() {
        FireBrigade fireBrigade = (FireBrigade)this.agentInfo.me();
        int water = fireBrigade.getWater();
        StandardEntityURN positionURN = this.worldInfo.getPosition(fireBrigade).getStandardURN();
        if(positionURN.equals(StandardEntityURN.REFUGE) && water < this.thresholdCompleted) {
            return fireBrigade.getPosition();
        }
        PathPlanning pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");
        if(positionURN.equals(StandardEntityURN.HYDRANT) && water < this.thresholdCompleted) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            } else {
                return fireBrigade.getPosition();
            }
        }
        if (water <= this.thresholdRefill) {
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
            List<EntityID> path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
            pathPlanning.setFrom(fireBrigade.getPosition());
            pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.HYDRANT));
            path = pathPlanning.calc().getResult();
            if(path != null && !path.isEmpty()) {
                return path.get(path.size() - 1);
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private EntityID calcTargetInCluster(Clustering clustering) {
        int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
        Collection<StandardEntity> elements = clustering.getClusterEntities(clusterIndex);
        if(elements == null || elements.isEmpty()) {
            return null;
        }
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : elements) {
            if (entity instanceof Building && ((Building)entity).isOnFire()) {
                fireBuildings.add(entity);
            }
        }
        for(StandardEntity entity : fireBuildings) {
            if(agents.isEmpty()) {
                break;
            } else if(agents.size() == 1) {
                if(agents.get(0).getID().getValue() == me.getID().getValue()) {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if(me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue()) {
                return entity.getID();
            } else {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    private EntityID calcTargetInWorld() {
        Collection<StandardEntity> entities = this.worldInfo.getEntitiesOfType(
                StandardEntityURN.BUILDING,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        StandardEntity me = this.agentInfo.me();
        List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.FIRE_BRIGADE));
        Set<StandardEntity> fireBuildings = new HashSet<>();
        for (StandardEntity entity : entities) {
            if (((Building)entity).isOnFire()) {
                fireBuildings.add(entity);
            }
        }
        for(StandardEntity entity : fireBuildings) {
            if(agents.isEmpty()) {
                break;
            } else if(agents.size() == 1) {
                if(agents.get(0).getID().getValue() == me.getID().getValue()) {
                    return entity.getID();
                }
                break;
            }
            agents.sort(new DistanceSorter(worldInfo, entity));
            StandardEntity a0 = agents.get(0);
            StandardEntity a1 = agents.get(1);

            if(me.getID().getValue() == a0.getID().getValue() || me.getID().getValue() == a1.getID().getValue()) {
                return entity.getID();
            } else {
                agents.remove(a0);
                agents.remove(a1);
            }
        }
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public BuildingSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public BuildingSelector preparate() {
        super.preparate();
        return this;
    }

    private boolean equalsPoint(int p1X, int p1Y, int p2X, int p2Y, int range) {
        return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
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
