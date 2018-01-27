package adf.sample.module.comm;

import adf.agent.communication.MessageManager;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.component.communication.ChannelSubscriber;
import rescuecore2.standard.entities.StandardEntityURN;

public class SampleChannelSubscriber extends ChannelSubscriber {


    @Override
    public void subscribe(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo,
                          MessageManager messageManager) {
        // subscribe only once at the beginning
        if (agentInfo.getTime() == 1) {
            int numChannels = scenarioInfo.getCommsChannelsCount()-1; // 0th channel is the voice channel

            int maxChannelCount = 0;
            boolean isPlatoon = isPlatoonAgent(agentInfo, worldInfo);
            if (isPlatoon) {
                maxChannelCount = scenarioInfo.getCommsChannelsMaxPlatoon();
            } else {
                maxChannelCount = scenarioInfo.getCommsChannelsMaxOffice();
            }

            StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);
            int[] channels = new int[maxChannelCount];
            int agentInc = 0;
            if (agentType == StandardEntityURN.FIRE_BRIGADE || agentType == StandardEntityURN.FIRE_STATION) {
                agentInc = 1;
            } else if (agentType == StandardEntityURN.POLICE_FORCE || agentType == StandardEntityURN.POLICE_OFFICE) {
                agentInc = 2;
            } else if (agentType == StandardEntityURN.AMBULANCE_TEAM || agentType == StandardEntityURN.AMBULANCE_CENTRE) {
                agentInc = 3;
            }

            for (int i = 0; i < maxChannelCount; i++) {
                int ch = (i*3)+agentInc;
                if (ch > numChannels) {
                    ch = (ch % numChannels)+1;
                }
                channels[i] = ch;
                System.out.println("[" + agentInfo.getID() + "] Subscribe to channel:" + ch + " numChannels:" + numChannels + " maxChannelCount:" + maxChannelCount);
            }

            messageManager.subscribeToChannels(channels);
        }
    }

    protected boolean isPlatoonAgent(AgentInfo agentInfo, WorldInfo worldInfo) {
        StandardEntityURN agentType = getAgentType(agentInfo, worldInfo);
        if (agentType == StandardEntityURN.FIRE_BRIGADE ||
                agentType == StandardEntityURN.POLICE_FORCE ||
                agentType == StandardEntityURN.AMBULANCE_TEAM) {
            return true;
        }
        return false;
    }

    protected StandardEntityURN getAgentType(AgentInfo agentInfo, WorldInfo worldInfo) {
        StandardEntityURN agentType = worldInfo.getEntity(agentInfo.getID()).getStandardURN();
        return agentType;
    }
}