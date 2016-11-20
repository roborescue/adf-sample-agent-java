package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandPolice;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleRoadDetector extends RoadDetector {
    private Set<EntityID> targetAreas;
    private Set<EntityID> priorityRoads;

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
            if (this.targetAreas.contains(positionID)) {
                this.result = positionID;
                return this;
            }
            List<EntityID> removeList = new ArrayList<>(this.priorityRoads.size());
            for(EntityID id : this.priorityRoads) {
                if(!this.targetAreas.contains(id)) {
                    removeList.add(id);
                }
            }
            this.priorityRoads.removeAll(removeList);
            if(this.priorityRoads.size() > 0) {
                this.pathPlanning.setFrom(positionID);
                this.pathPlanning.setDestination(this.targetAreas);
                List<EntityID> path = this.pathPlanning.calc().getResult();
                if (path != null && path.size() > 0) {
                    this.result = path.get(path.size() - 1);
                }
                return this;
            }


            this.pathPlanning.setFrom(positionID);
            this.pathPlanning.setDestination(this.targetAreas);
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
        this.targetAreas = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.targetAreas.add(id);
                }
            }
        }
        this.priorityRoads = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.priorityRoads.add(id);
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
        this.targetAreas = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE, BUILDING, GAS_STATION)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.targetAreas.add(id);
                }
            }
        }
        this.priorityRoads = new HashSet<>();
        for(StandardEntity e : this.worldInfo.getEntitiesOfType(REFUGE)) {
            for(EntityID id : ((Building)e).getNeighbours()) {
                StandardEntity neighbour = this.worldInfo.getEntity(id);
                if(neighbour instanceof Road) {
                    this.priorityRoads.add(id);
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
                        this.targetAreas.remove(this.result);
                        this.result = null;
                    }
                }
            }
        }
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(mat.getAction() == MessageAmbulanceTeam.ACTION_RESCUE) {
                    StandardEntity position = this.worldInfo.getEntity(mat.getPosition());
                    if(position != null && position instanceof Building) {
                        this.targetAreas.removeAll(((Building)position).getNeighbours());
                    }
                } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
                    StandardEntity position = this.worldInfo.getEntity(mat.getPosition());
                    if (position != null && position instanceof Building) {
                        this.targetAreas.removeAll(((Building) position).getNeighbours());
                    }
                } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_MOVE) {
                    StandardEntity target = this.worldInfo.getEntity(mat.getTargetID());
                    if(target instanceof Building) {
                        for (EntityID id : ((Building)target).getNeighbours()) {
                            StandardEntity neighbour = this.worldInfo.getEntity(id);
                            if (neighbour instanceof Road) {
                                this.priorityRoads.add(id);
                            }
                        }
                    } else if(target instanceof Human) {
                        Human human = (Human)target;
                        if(human.isPositionDefined()) {
                            StandardEntity position = this.worldInfo.getPosition(human);
                            if(position instanceof Building) {
                                for (EntityID id : ((Building)position).getNeighbours()) {
                                    StandardEntity neighbour = this.worldInfo.getEntity(id);
                                    if (neighbour instanceof Road) {
                                        this.priorityRoads.add(id);
                                    }
                                }
                            }
                        }
                    }
                }
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade)message;
                if(mfb.getAction() == MessageFireBrigade.ACTION_REFILL) {
                    StandardEntity target = this.worldInfo.getEntity(mfb.getTargetID());
                    if(target instanceof Building) {
                        for (EntityID id : ((Building)target).getNeighbours()) {
                            StandardEntity neighbour = this.worldInfo.getEntity(id);
                            if (neighbour instanceof Road) {
                                this.priorityRoads.add(id);
                            }
                        }
                    } else if(target.getStandardURN() == HYDRANT) {
                        this.priorityRoads.add(target.getID());
                        this.targetAreas.add(target.getID());
                    }
                }
            } else if(messageClass == MessageRoad.class) {
                MessageRoad messageRoad = (MessageRoad)message;
                if(messageRoad.isBlockadeDefined() && !changedEntities.contains(messageRoad.getBlockadeID())) {
                    MessageUtil.reflectMessage(this.worldInfo, messageRoad);
                }
                if(messageRoad.isPassable()) {
                    this.targetAreas.remove(messageRoad.getRoadID());
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce)message;
                if(mpf.getAction() == MessagePoliceForce.ACTION_CLEAR) {
                    if(mpf.getAgentID().getValue() != this.agentInfo.getID().getValue()) {
                        if (mpf.isTargetDefined()) {
                            EntityID targetID = mpf.getTargetID();
                            StandardEntity entity = this.worldInfo.getEntity(targetID);
                            if (entity != null) {
                                if (entity instanceof Area) {
                                    this.targetAreas.remove(targetID);
                                    if(this.result != null && this.result.getValue() == targetID.getValue()) {
                                        if(this.agentInfo.getID().getValue() < mpf.getAgentID().getValue()) {
                                            this.result = null;
                                        }
                                    }
                                } else if (entity.getStandardURN() == BLOCKADE) {
                                    EntityID position = ((Blockade) entity).getPosition();
                                    this.targetAreas.remove(position);
                                    if(this.result != null && this.result.getValue() == position.getValue()) {
                                        if(this.agentInfo.getID().getValue() < mpf.getAgentID().getValue()) {
                                            this.result = null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if(messageClass == CommandPolice.class) {
                CommandPolice command = (CommandPolice)message;
                boolean flag = false;
                if(command.isToIDDefined() && this.agentInfo.getID().getValue() == command.getToID().getValue()) {
                    flag = true;
                } else if(command.isBroadcast()) {
                    flag = true;
                }
                if(flag && command.getAction() == CommandPolice.ACTION_CLEAR) {
                    StandardEntity target = this.worldInfo.getEntity(command.getTargetID());
                    if(target instanceof Area) {
                        this.priorityRoads.add(target.getID());
                        this.targetAreas.add(target.getID());
                    } else if(target.getStandardURN() == BLOCKADE) {
                        Blockade blockade = (Blockade)target;
                        if(blockade.isPositionDefined()) {
                            this.priorityRoads.add(blockade.getPosition());
                            this.targetAreas.add(blockade.getPosition());
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
                    this.targetAreas.remove(id);
                }
            }
        }
        return this;
    }
}
