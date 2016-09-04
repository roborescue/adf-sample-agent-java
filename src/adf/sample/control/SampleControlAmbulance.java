package adf.sample.control;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.topdown.CommandAmbulance;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.control.ControlAmbulance;
import org.dom4j.Entity;
import rescuecore2.messages.Message;
import rescuecore2.standard.entities.AmbulanceTeam;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;
import sample.DistanceSorter;

import java.util.*;

public class SampleControlAmbulance extends ControlAmbulance {
    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {

    }

    @Override
    public void think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData debugData) {
        Collection<EntityID> commanders = worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_CENTRE);
        Collection<EntityID> refuges = worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE);
        Set<EntityID> changedEntities = worldInfo.getChanged().getChangedEntities();
        Set<Human> priorities = new HashSet<>();
        Set<Human> victims = new HashSet<>();
        List<StandardEntity> agents = new ArrayList<>(worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM));
        for(CommunicationMessage message : messageManager.getReceivedMessageList(MessageCivilian.class)) {
            MessageCivilian mc = (MessageCivilian)message;
            if(!changedEntities.contains(mc.getAgentID())){
                MessageUtil.reflectMessage(worldInfo, mc);
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == CommandAmbulance.class) {
                CommandAmbulance command = (CommandAmbulance)message;
                if(!commanders.contains(command.getSenderID())) {
                    if(command.getAction() == CommandAmbulance.ACTION_RESCUE) {
                        StandardEntity entity = worldInfo.getEntity(command.getTargetID());
                        if(entity instanceof Human) {
                            Human human = (Human)entity;
                            if(human.isHPDefined() && human.isBuriednessDefined()
                                    && human.getHP() > 0 && human.getBuriedness() > 0) {
                                if(human.getStandardURN() != StandardEntityURN.CIVILIAN) {
                                    priorities.add(human);
                                } else {
                                    victims.add(human);
                                }
                            }
                        }
                    } else if(command.getAction() == CommandAmbulance.ACTION_LOAD) {
                        StandardEntity entity = worldInfo.getEntity(command.getTargetID());
                        if(entity instanceof Human) {
                            Human human = (Human)entity;
                            if(human.isHPDefined() && human.getHP() > 0) {
                                victims.add(human);

                            }
                        }
                    }
                }
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())){
                    MessageUtil.reflectMessage(worldInfo, mat);
                }
                if(mat.getAgentID().getValue() == mat.getSenderID().getValue()) {
                    AmbulanceTeam ambulance = (AmbulanceTeam)worldInfo.getEntity(mat.getAgentID());
                    if(mat.getBuriedness() > 0) {
                        priorities.add(ambulance);
                        agents.remove(ambulance);
                    } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_RESCUE) {
                        agents.remove(ambulance);
                    } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
                        agents.remove(ambulance);
                    } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_MOVE) {
                        if(refuges.contains(mat.getTargetID())) {
                            agents.remove(ambulance);
                        }
                    }
                }
            }
        }
        for(Human human : priorities) {
            if (agents.isEmpty()) {
                return;
            }
            agents.sort(new DistanceSorter(worldInfo, human));
            messageManager.addMessage(new CommandAmbulance(
                    true,
                    agents.get(0).getID(),
                    human.getID(),
                    CommandAmbulance.ACTION_RESCUE
            ));
        }
        for(Human human : victims) {
            if (agents.isEmpty()) {
                return;
            }
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
