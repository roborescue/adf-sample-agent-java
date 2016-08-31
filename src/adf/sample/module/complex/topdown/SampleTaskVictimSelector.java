package adf.sample.module.complex.topdown;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.topdown.CommandAmbulance;
import adf.agent.communication.standard.bundle.topdown.CommandScout;
import adf.agent.communication.standard.bundle.topdown.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.HumanSelector;
import rescuecore2.standard.entities.Area;
import rescuecore2.standard.entities.Human;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;

import static rescuecore2.standard.entities.StandardEntityURN.AMBULANCE_CENTRE;

public class SampleTaskVictimSelector  extends HumanSelector {

    private int action;
    private EntityID task;
    private EntityID senderID;

    public SampleTaskVictimSelector(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.action = -1;
        this.task = null;
        this.senderID = null;
    }

    @Override
    public HumanSelector updateInfo(MessageManager messageManager) {
        if(this.task != null && this.isCompleted()) {
            messageManager.addMessage(new MessageReport(true, true, false, this.senderID));
            this.action = -1;
            this.task = null;
            this.senderID = null;
        }
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> commanders = this.worldInfo.getEntityIDsOfType(AMBULANCE_CENTRE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout) message;
            if(commanders.contains(command.getSenderID())) {
                if(command.getToID().getValue() == agentID.getValue()) {
                    this.action = -1;
                    this.task = null;
                    this.senderID = null;
                }
            }
        }
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance) message;
            if(!commanders.contains(command.getSenderID())) {
                continue;
            }
            if(command.isBroadcast() || command.getToID().getValue() != agentID.getValue()) {
                continue;
            }
            if(command.getAction() == CommandAmbulance.ACTION_RESCUE) {
                this.action = CommandAmbulance.ACTION_RESCUE;
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return this;
            } else if(command.getAction() == CommandAmbulance.ACTION_LOAD) {
                this.action = CommandAmbulance.ACTION_LOAD;
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return this;
            } else if(command.getAction() == CommandAmbulance.ACTION_UNLOAD) {
                this.action = CommandAmbulance.ACTION_UNLOAD;
                this.task = command.getTargetID();
                this.senderID = command.getSenderID();
                return this;
            } else {
                this.action = -1;
                this.task = null;
                this.senderID = null;
            }
        }

        Human transportHuman =  this.agentInfo.someoneOnBoard();
        if(transportHuman != null) {
            if(this.task != null) {
                if (this.action != CommandAmbulance.ACTION_UNLOAD) {
                    messageManager.addMessage(new MessageReport(true, false, false, this.senderID));
                    this.action =  CommandAmbulance.ACTION_UNLOAD;
                    this.task = transportHuman.getID();
                    this.senderID = null;
                }
            } else {
                this.action =  CommandAmbulance.ACTION_UNLOAD;
                this.task = transportHuman.getID();
                this.senderID = null;
            }
        }
        return this;
    }

    private boolean isCompleted() {
        StandardEntity entity = this.worldInfo.getEntity(this.task);

        if(this.action == CommandAmbulance.ACTION_RESCUE) {
            Human human = (Human)entity;
            if(human.isBuriednessDefined() && human.getBuriedness() == 0) {
                return true;
            }
        } else if(this.action == CommandAmbulance.ACTION_LOAD) {
            Human human = (Human)entity;
            if(human.getStandardURN() == StandardEntityURN.CIVILIAN) {
                Human civilian = this.agentInfo.someoneOnBoard();
                if(civilian != null && this.task.getValue() == civilian.getID().getValue()) {
                    return true;
                }
            } else {
                if(human.isBuriednessDefined() && human.getBuriedness() == 0) {
                    return true;
                }
            }
        } else if(this.action == CommandAmbulance.ACTION_UNLOAD) {
            if(entity instanceof Area) {
                if(this.task.getValue() == this.agentInfo.getPosition().getValue()) {
                    Human civilian = this.agentInfo.someoneOnBoard();
                    if(civilian == null) {
                        return true;
                    }
                }
            } else {
                Human civilian = this.agentInfo.someoneOnBoard();
                if(civilian == null) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public HumanSelector calc() {
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.task;
    }

    @Override
    public HumanSelector precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public HumanSelector resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        return this;
    }

    @Override
    public HumanSelector preparate() {
        super.preparate();
        return this;
    }
}