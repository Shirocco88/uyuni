import * as React from "react";
import SpaRenderer from "core/spa/spa-renderer";
import { RolesProvider } from "core/auth/roles-context";
import { MessagesContainer } from "components/toastr/toastr";
import { ServerMessageType } from "components/messages";

import Cluster from "./cluster";

type RendererProps = {
  cluster?: string;
  flashMessage?: ServerMessageType;
};

export const renderer = (id: string, { cluster, flashMessage }: RendererProps = {}) => {
  let clusterJson: any = {};
  try {
    clusterJson = JSON.parse(cluster || "");
  } catch (error) {
    console.log(error);
  }

  SpaRenderer.renderNavigationReact(
    <RolesProvider>
      <MessagesContainer />
      <Cluster cluster={clusterJson} flashMessage={flashMessage} />
    </RolesProvider>,
    document.getElementById(id)
  );
};
