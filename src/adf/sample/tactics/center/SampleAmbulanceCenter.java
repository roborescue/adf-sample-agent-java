package adf.sample.tactics.center;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.tactics.center.TacticsAmbulanceCenter;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

public class SampleAmbulanceCenter extends TacticsAmbulanceCenter {
    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {

    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {
        Collection<EntityID> refuges = worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        Set<Human> priorities = new HashSet<>();
        Set<Human> victims = new HashSet<>();
        Collection<EntityID> agentIDs = worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM);
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageCivilian.class)) {
            MessageCivilian mc = (MessageCivilian) message;
            MessageUtil.reflectMessage(worldInfo, mc);
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance) message;
            if (command.isBroadcast()) {
                if (command.getAction() == CommandAmbulance.ACTION_RESCUE) {
                    StandardEntity entity = worldInfo.getEntity(command.getTargetID());
                    if (entity instanceof Human) {
                        Human human = (Human) entity;
                        if (human.isHPDefined() && human.isBuriednessDefined()
                                && human.getHP() > 0 && human.getBuriedness() > 0) {
                            if (human.getStandardURN() != StandardEntityURN.CIVILIAN) {
                                priorities.add(human);
                            } else {
                                victims.add(human);
                            }
                        }
                    }
                } else if (command.getAction() == CommandAmbulance.ACTION_LOAD) {
                    StandardEntity entity = worldInfo.getEntity(command.getTargetID());
                    if (entity instanceof Civilian) {
                        Civilian civilian = (Civilian) entity;
                        if (civilian.isHPDefined() && civilian.getHP() > 0) {
                            victims.add(civilian);
                        }
                    }
                }
            }
        }
        for (CommunicationMessage message : messageManager.getReceivedMessageList(MessageAmbulanceTeam.class)) {
            MessageAmbulanceTeam mat = (MessageAmbulanceTeam) message;
            if (mat.getAgentID().getValue() == mat.getSenderID().getValue()) {
                AmbulanceTeam ambulance = MessageUtil.reflectMessage(worldInfo, mat);
                if (mat.getBuriedness() > 0) {
                    priorities.add(ambulance);
                    agentIDs.remove(ambulance.getID());
                } else if (mat.getAction() == MessageAmbulanceTeam.ACTION_RESCUE) {
                    StandardEntity entity = worldInfo.getEntity(mat.getTargetID());
                    if(entity instanceof Human) {
                        Human human = (Human)entity;
                        if(human.isBuriednessDefined() && human.getBuriedness() > 0) {
                            agentIDs.remove(ambulance.getID());
                        }
                    }
                } else if (mat.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
                    agentIDs.remove(ambulance.getID());
                } else if (mat.getAction() == MessageAmbulanceTeam.ACTION_MOVE) {
                    if (refuges.contains(mat.getTargetID())) {
                        agentIDs.remove(ambulance.getID());
                    }
                }
            }
        }
        List<StandardEntity> agents = new ArrayList<>();
        for(EntityID id : agentIDs) {
            agents.add(worldInfo.getEntity(id));
        }
        for(Human human : priorities) {
            if (agents.isEmpty()) { return; }
            agents.sort(new DistanceSorter(worldInfo, human));
            messageManager.addMessage(new CommandAmbulance(
                    true,
                    agents.get(0).getID(),
                    human.getID(),
                    CommandAmbulance.ACTION_RESCUE
            ));
        }
        for(Human human : victims) {
            if (agents.isEmpty()) { return; }
            agents.sort(new DistanceSorter(worldInfo, human));
            messageManager.addMessage(new CommandAmbulance(
                    true,
                    agents.get(0).getID(),
                    human.getID(),
                    CommandAmbulance.ACTION_RESCUE
            ));
        }
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeInfo, DevelopData debugData) {
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData debugData) {
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
