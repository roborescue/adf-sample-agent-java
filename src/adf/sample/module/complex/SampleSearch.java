package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleSearch extends Search {
    private PathPlanning pathPlanning;
    private Clustering clustering;

    private EntityID result;
    private Collection<EntityID> unsearchedBuildingIDs;

    private boolean isSendBuildingMessage;
    private boolean isSendCivilianMessage;
    private boolean isSendRoadMessage;
    private Collection<EntityID> agentPositions;
    private Map<EntityID, Integer> sentTimeMap;
    private int sendingAvoidTimeReceived;
    private int sendingAvoidTimeSent;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);

        this.unsearchedBuildingIDs = new HashSet<>();

        this.isSendBuildingMessage = true;
        this.isSendCivilianMessage = true;
        this.isSendRoadMessage = true;
        this.agentPositions = new HashSet<>();
        this.sentTimeMap = new HashMap<>();
        this.sendingAvoidTimeReceived = developData.getInteger("SampleSearch.sendingAvoidTimeReceived", 3);
        this.sendingAvoidTimeSent = developData.getInteger("SampleSearch.sendingAvoidTimeSent", 5);

        StandardEntityURN agentURN = ai.me().getStandardURN();
        switch  (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case PRECOMPUTED:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case NON_PRECOMPUTE:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
        }
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);

        this.reflectMessage(messageManager);
        this.sendEntityInfo(messageManager);

        if(this.unsearchedBuildingIDs.isEmpty()) {
            this.reset();
        }
        this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());

        if(this.unsearchedBuildingIDs.isEmpty()) {
            this.reset();
            this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());
        }
        return this;
    }

    @Override
    public Search calc() {
        this.result = null;
        this.pathPlanning.setFrom(this.agentInfo.getPosition());
        this.pathPlanning.setDestination(this.unsearchedBuildingIDs);
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    private void reset() {
        this.unsearchedBuildingIDs.clear();

        Collection<StandardEntity> clusterEntities = null;
        if(this.clustering != null) {
            int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
            clusterEntities = this.clustering.getClusterEntities(clusterIndex);

        }
        if(clusterEntities != null && clusterEntities.size() > 0) {
            for(StandardEntity entity : clusterEntities) {
                if(entity instanceof Building && entity.getStandardURN() != REFUGE) {
                    this.unsearchedBuildingIDs.add(entity.getID());
                }
            }
        } else {
            this.unsearchedBuildingIDs.addAll(this.worldInfo.getEntityIDsOfType(
                    BUILDING,
                    GAS_STATION,
                    AMBULANCE_CENTRE,
                    FIRE_STATION,
                    POLICE_OFFICE
            ));
        }
    }

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
        int time = this.agentInfo.getTime();

        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageBuilding.class) {
                MessageBuilding mb = (MessageBuilding)message;
                if(!changedEntities.contains(mb.getBuildingID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mb);
                }
                this.sentTimeMap.put(mb.getBuildingID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageRoad.class) {
                MessageRoad mr = (MessageRoad)message;
                if(mr.isBlockadeDefined() && !changedEntities.contains(mr.getBlockadeID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mr);
                    this.sentTimeMap.put(mr.getBlockadeID(), time + this.sendingAvoidTimeReceived);
                }
                this.sentTimeMap.put(mr.getRoadID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!changedEntities.contains(mc.getAgentID())){
                    MessageUtil.reflectMessage(this.worldInfo, mc);
                }
                this.sentTimeMap.put(mc.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mat);
                }
                this.sentTimeMap.put(mat.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!changedEntities.contains(mfb.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mfb);
                }
                this.sentTimeMap.put(mfb.getAgentID(), time + this.sendingAvoidTimeReceived);
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mpf);
                }
                this.sentTimeMap.put(mpf.getAgentID(), time + this.sendingAvoidTimeReceived);
            }
        }
    }

    private void sendEntityInfo(MessageManager messageManager) {
        Human agent = (Human) this.agentInfo.me();
        this.checkSendFlags(agent);
        Building building = null;
        Civilian civilian = null;
        Road road = null;

        EntityID position = agent.getPosition();
        int currentTime = this.agentInfo.getTime();
        for(EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            Integer time = this.sentTimeMap.get(id);
            if(time != null && time > currentTime) {
                continue;
            }
            StandardEntity entity = this.worldInfo.getEntity(id);
            EntityID targetID = entity.getID();
            if(entity instanceof Building && this.isSendBuildingMessage) {
                if(!this.agentPositions.contains(targetID)) {
                    building = this.selectBuilding(building, (Building) entity);
                } else if(targetID.getValue() == position.getValue()) {
                    building = this.selectBuilding(building, (Building) entity);
                }
            } else if(entity instanceof Road && this.isSendRoadMessage) {
                if(!this.agentPositions.contains(targetID)) {
                    road = this.selectRoad(road, (Road) entity);
                } else if(targetID.getValue() == position.getValue()) {
                    road = this.selectRoad(road, (Road) entity);
                }
            } else if(entity.getStandardURN() == CIVILIAN && this.isSendCivilianMessage) {
                Civilian target = (Civilian) entity;
                if(!this.agentPositions.contains(target.getPosition())) {
                    civilian = this.selectCivilian(civilian, target);
                } else if(target.getPosition().getValue() == position.getValue()) {
                    civilian = this.selectCivilian(civilian, target);
                }
            } else if(entity.getStandardURN() == BLOCKADE && this.isSendRoadMessage) {
                Blockade blockade = (Blockade) entity;
                if(blockade.isPositionDefined()) {
                    StandardEntity blockadePosition = this.worldInfo.getEntity(blockade.getPosition());
                    if(blockadePosition instanceof Road) {
                        Road target = (Road) blockadePosition;
                        if (!this.agentPositions.contains(target.getID())) {
                            road = this.selectRoad(road, target);
                        } else if (target.getID().getValue() == position.getValue()) {
                            road = this.selectRoad(road, target);
                        }
                    }
                }
            }
        }

        /*if(this.isSendBuildingMessage && building != null) {
            messageManager.addMessage(new MessageBuilding(true, building));
            this.sentTimeMap.put(building.getID(), currentTime + this.sendingAvoidTimeSent);
        }
        if(this.isSendCivilianMessage && civilian != null) {
            messageManager.addMessage(new MessageCivilian(true, civilian));
            this.sentTimeMap.put(civilian.getID(), currentTime + this.sendingAvoidTimeSent);
        }
        if(this.isSendRoadMessage && road != null) {
            if(road.isBlockadesDefined() && road.getBlockades().size() > 0) {
                Blockade blockade = (Blockade) this.worldInfo.getEntity(road.getBlockades().get(0));
                messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                this.sentTimeMap.put(road.getID(), currentTime + this.sendingAvoidTimeSent);
            }
        }*/
    }

    private void checkSendFlags(Human agent){
        this.isSendBuildingMessage = true;
        this.isSendCivilianMessage = true;
        this.isSendRoadMessage = true;

        EntityID agentID = agent.getID();
        EntityID position = agent.getPosition();
        StandardEntityURN agentURN = agent.getStandardURN();
        EnumSet<StandardEntityURN> agentTypes = EnumSet.of(AMBULANCE_TEAM, FIRE_BRIGADE, POLICE_FORCE);
        agentTypes.remove(agentURN);

        this.agentPositions.clear();
        for(StandardEntity entity : this.worldInfo.getEntitiesOfType(agentURN)) {
            Human other = (Human)entity;
            if(other.getPosition().getValue() == position.getValue()) {
                if(other.getID().getValue() > agentID.getValue()) {
                    this.isSendBuildingMessage = false;
                    this.isSendCivilianMessage = false;
                    this.isSendRoadMessage = false;
                }
            }
            this.agentPositions.add(other.getPosition());
        }

        for(StandardEntityURN urn : agentTypes) {
            for(StandardEntity entity : this.worldInfo.getEntitiesOfType(urn)) {
                Human other = (Human)entity;
                if(other.getPosition().getValue() != position.getValue()) {
                    continue;
                }
                if(urn == AMBULANCE_TEAM) {
                    this.isSendCivilianMessage = false;
                    if(other.getID().getValue() > agentID.getValue()) {
                        if (agentURN == FIRE_BRIGADE) {
                            this.isSendRoadMessage = false;
                        } else if(agentURN == POLICE_FORCE) {
                            this.isSendBuildingMessage = false;
                        }
                    }
                } else if(urn == FIRE_BRIGADE) {
                    this.isSendBuildingMessage = false;
                    if(other.getID().getValue() > agentID.getValue()) {
                        if (agentURN == AMBULANCE_TEAM) {
                            this.isSendRoadMessage = false;
                        } else if(agentURN == POLICE_FORCE) {
                            this.isSendCivilianMessage = false;
                        }
                    }
                } else if(urn == POLICE_FORCE) {
                    this.isSendRoadMessage = false;
                    if(other.getID().getValue() > agentID.getValue()) {
                        if (agentURN == AMBULANCE_TEAM) {
                            this.isSendBuildingMessage = false;
                        } else if(agentURN == FIRE_BRIGADE) {
                            this.isSendCivilianMessage = false;
                        }
                    }
                }
                this.agentPositions.add(other.getPosition());
            }
        }
    }

    private Building selectBuilding(Building building1, Building building2) {
        if(building1 != null) {
            if(building2 != null) {
                if(building1.isOnFire() && building2.isOnFire()) {
                    if (building1.getFieryness() < building2.getFieryness()) {
                        return building2;
                    } else if (building1.getFieryness() > building2.getFieryness()) {
                        return building1;
                    }
                    if(building1.isTemperatureDefined() && building2.isTemperatureDefined()) {
                        return (building1.getTemperature() < building2.getTemperature()) ? building2 : building1;
                    }
                } else if (!building1.isOnFire() && building2.isOnFire()) {
                    return building2;
                }
            }
            return building1;
        }
        return (building2 != null) ? building2 : null;
    }

    private Civilian selectCivilian(Civilian civilian1, Civilian civilian2) {
        if(this.checkCivilian(civilian1, true)) {
            if(this.checkCivilian(civilian2, true)) {
                if(civilian1.getHP() > civilian2.getHP()) {
                    return civilian1;
                } else if(civilian1.getHP() < civilian2.getHP()) {
                    return civilian2;
                }else {
                    if(civilian1.isBuriednessDefined()) {
                        if(civilian2.isBuriednessDefined()) {
                            if (civilian1.getBuriedness() < civilian2.getBuriedness()) {
                                return civilian1;
                            } else if (civilian1.getBuriedness() > civilian2.getBuriedness()) {
                                return civilian2;
                            }
                        } else {
                            return civilian1;
                        }
                    } else if(civilian2.isBuriednessDefined()) {
                        return civilian2;
                    }
                    if(civilian1.isDamageDefined()) {
                        if(civilian2.isDamageDefined()) {
                            if(civilian1.getDamage() < civilian2.getDamage()) {
                                return civilian1;
                            } else if(civilian1.getDamage() > civilian2.getDamage()) {
                                return civilian2;
                            }
                        } else {
                            return civilian1;
                        }
                    } else if(civilian2.isDamageDefined()) {
                        return civilian2;
                    }
                }
            }
            return civilian1;
        }
        if(this.checkCivilian(civilian2, true)) {
            return civilian2;
        } else if(this.checkCivilian(civilian1, false)) {
            return civilian1;
        } else if(this.checkCivilian(civilian2, false)) {
            return civilian2;
        }
        return null;
    }

    private boolean checkCivilian(Civilian c, boolean checkOtherValues) {
        if(c != null && c.isHPDefined() && c.isPositionDefined()) {
            if(checkOtherValues && (!c.isDamageDefined() && !c.isBuriednessDefined())) {
                return false;
            }
            return true;
        }
        return false;
    }

    private Road selectRoad(Road road1, Road road2) {
        if(road1 != null && road1.isBlockadesDefined()) {
            if(road2 != null && road2.isBlockadesDefined()) {
                if(road1.getID().getValue() == road2.getID().getValue()) {
                    return road1;
                }
                int cost1 = this.getRepairCost(road1);
                int cost2 = this.getRepairCost(road2);
                if(cost1 > cost2) {
                    return road1;
                } else if(cost1 < cost2) {
                    return road2;
                } else { // cost1 == cost2
                    if((cost1 + cost2) > 0) {
                        if (road1.getNeighbours().size() > road2.getNeighbours().size()) {
                            return road1;
                        } else if (road1.getNeighbours().size() < road2.getNeighbours().size()) {
                            return road2;
                        }
                    }
                }
            }
            if(road1.getBlockades().size() > 0) {
                return road1;
            }
        }
        if(road2 != null && road2.isBlockadesDefined() && road2.getBlockades().size() > 0) {
            return road2;
        }
        return null;
    }

    private int getRepairCost(Road road) {
        int cost = 0;
        if(road.isBlockadesDefined()) {
            for(EntityID id : road.getBlockades()) {
                Blockade blockade = (Blockade) this.worldInfo.getEntity(id);
                if(blockade != null && blockade.isRepairCostDefined()) {
                    cost += blockade.getRepairCost();
                }
            }
        }
        return cost;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public Search precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.worldInfo.requestRollback();
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        return this;
    }

    @Override
    public Search preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.worldInfo.requestRollback();
        this.pathPlanning.preparate();
        this.clustering.preparate();
        return this;
    }
}