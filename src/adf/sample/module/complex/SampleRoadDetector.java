package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.communication.standard.bundle.information.MessageRoad;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadDetector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleRoadDetector extends RoadDetector {
    private HashSet<EntityID> impassableNeighbours;

    private PathPlanning pathPlanning;

    private EntityID result;

    public SampleRoadDetector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        switch  (scenarioInfo.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("SampleRoadDetector.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                break;
        }
        this.result = null;
    }

    @Override
    public RoadDetector calc() {
        if(this.result == null) {
            EntityID positionID = this.agentInfo.getPosition();
            if (this.impassableNeighbours.contains(positionID)) {
                this.result = positionID;
                return this;
            }

            this.pathPlanning.setFrom(positionID);
            this.pathPlanning.setDestination(this.impassableNeighbours);
            List<EntityID> path = this.pathPlanning.calc().getResult();
            if (path != null && path.size() > 0) {
                this.result = path.get(path.size() - 1);
            }
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public RoadDetector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        return this;
    }

    @Override
    public RoadDetector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.impassableNeighbours = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.impassableNeighbours.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public RoadDetector preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.impassableNeighbours = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.impassableNeighbours.add(id);
                }
            }
        }
        return this;
    }

    @Override
    public RoadDetector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        if(this.result != null) {
            if(this.agentInfo.getPosition().equals(this.result)) {
                StandardEntity entity = this.worldInfo.getEntity(this.result);
                if(entity instanceof Building) {
                    this.result = null;
                } else if(entity instanceof Road) {
                    Road road = (Road)entity;
                    if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                        this.impassableNeighbours.remove(this.result);
                        this.result = null;
                    }
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(mat.getAction() == MessageAmbulanceTeam.ACTION_RESCUE) {
                    StandardEntity position = this.worldInfo.getEntity(mat.getPosition());
                    if(position != null && position instanceof Building) {
                        this.impassableNeighbours.removeAll(((Building)position).getNeighbours());
                    }
                } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
                    StandardEntity position = this.worldInfo.getEntity(mat.getPosition());
                    if(position != null && position instanceof Building) {
                        this.impassableNeighbours.removeAll(((Building)position).getNeighbours());
                    }
                }
            } else if(messageClass == MessageRoad.class) {
                MessageRoad messageRoad = (MessageRoad)message;
                if(messageRoad.isPassable()) {
                    this.impassableNeighbours.remove(messageRoad.getRoadID());
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce)message;
                if(mpf.getAction() == MessagePoliceForce.ACTION_CLEAR) {
                    if(mpf.getAgentID().getValue() != this.agentInfo.getID().getValue()) {
                        if (mpf.isTargetDefined()) {
                            StandardEntity entity = this.worldInfo.getEntity(mpf.getTargetID());
                            if (entity != null) {
                                if (entity instanceof Area) {
                                    this.impassableNeighbours.remove(entity.getID());
                                } else if (entity.getStandardURN() == BLOCKADE) {
                                    this.impassableNeighbours.remove(((Blockade) entity).getPosition());
                                }
                            }
                        }
                    }
                }
            }
        }
        for(EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(id);
            if(entity instanceof Road) {
                Road road = (Road)entity;
                if(!road.isBlockadesDefined() || road.getBlockades().isEmpty()) {
                    this.impassableNeighbours.remove(id);
                }
            }
        }
        return this;
    }
}
