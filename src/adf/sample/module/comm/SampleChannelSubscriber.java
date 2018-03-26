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
            for (int i = 0; i < maxChannelCount; i++) {
                channels[i] = getChannelNumber(agentType, i, numChannels);
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

    public static int getChannelNumber(StandardEntityURN agentType, int channelIndex, int numChannels) {
        int agentIndex = 0;
        if (agentType == StandardEntityURN.FIRE_BRIGADE || agentType == StandardEntityURN.FIRE_STATION) {
            agentIndex = 1;
        } else if (agentType == StandardEntityURN.POLICE_FORCE || agentType == StandardEntityURN.POLICE_OFFICE) {
            agentIndex = 2;
        } else if (agentType == StandardEntityURN.AMBULANCE_TEAM || agentType == StandardEntityURN.AMBULANCE_CENTRE) {
            agentIndex = 3;
        }

        int index = (3*channelIndex)+agentIndex;
        if ((index%numChannels) == 0) {
            index = numChannels;
        } else {
            index = index % numChannels;
        }
        return index;
    }

    public static void main(String[] args) {
        int numChannels = 6;
        int maxChannels = 2;
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("FIREBRIGADE-" + i + ":" + getChannelNumber(StandardEntityURN.FIRE_BRIGADE, i, numChannels));
        }
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("POLICE-" + i + ":" + getChannelNumber(StandardEntityURN.POLICE_OFFICE, i, numChannels));
        }
        for (int i = 0; i < maxChannels; i++) {
            System.out.println("AMB-" + i + ":" + getChannelNumber(StandardEntityURN.AMBULANCE_CENTRE, i, numChannels));
        }
    }
}