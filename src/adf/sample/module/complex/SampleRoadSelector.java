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
import adf.component.module.complex.RoadSelector;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.HashSet;
import java.util.List;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleRoadSelector extends RoadSelector {
    private HashSet<EntityID> impassableNeighbours;

    private EntityID result;

    public SampleRoadSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.result = null;
    }

    @Override
    public RoadSelector calc() {
        if(this.result == null) {
            EntityID positionID = this.agentInfo.getPosition();
            if (this.impassableNeighbours.contains(positionID)) {
                this.result = positionID;
                return this;
            }
            PathPlanning pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
            pathPlanning.setFrom(positionID);
            pathPlanning.setDestination(this.impassableNeighbours);
            List<EntityID> path = pathPlanning.calc().getResult();
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
    public RoadSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public RoadSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
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
    public RoadSelector preparate() {
        super.preparate();
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
    public RoadSelector updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
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
        for(EntityID id : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
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
