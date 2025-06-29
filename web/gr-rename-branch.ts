/**
 * @license
 * Copyright (C) 2025 AudioCodes Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {PluginApi} from '@gerritcodereview/typescript-api/plugin';
import {
  ActionInfo,
  ConfigInfo,
  RepoName,
} from '@gerritcodereview/typescript-api/rest-api';
import {css, CSSResult, html, LitElement} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';

interface RenameBranchInput {
  source_branch: string;
  destination_branch: string;
  include_closed_changes?: boolean;
  message?: string;
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-rename-branch': GrRenameBranch;
  }
  interface Window {
    CANONICAL_PATH?: string;
  }
}

@customElement('gr-rename-branch')
export class GrRenameBranch extends LitElement {
  @query('#renameBranchModal')
  renameBranchModal?: HTMLDialogElement;

  @query('#sourceBranchInput')
  sourceBranchInput?: HTMLInputElement;

  @query('#destinationBranchInput')
  destinationBranchInput?: HTMLInputElement;

  @query('#includeClosedChangesCheckBox')
  includeClosedChangesCheckBox?: HTMLInputElement;

  @query('#messageInput')
  messageInput?: HTMLTextAreaElement;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  plugin!: PluginApi;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: Object})
  config!: ConfigInfo;

  /** Guaranteed to be provided by the 'repo-command' endpoint. */
  @property({type: String})
  repoName!: RepoName;

  @state()
  private error?: string;

  static override get styles() {
    return [
      window.Gerrit.styles.font as CSSResult,
      window.Gerrit.styles.modal as CSSResult,
      css`
        :host {
          display: block;
          margin-bottom: var(--spacing-xxl);
        }
        h2 {
          margin-top: var(--spacing-xxl);
          margin-bottom: var(--spacing-s);
        }
        .error {
          color: red;
        }
        .form-section {
          margin-bottom: var(--spacing-l);
        }
        .form-section label {
          display: block;
          margin-bottom: var(--spacing-xs);
          font-weight: var(--font-weight-bold);
        }
        .form-section input,
        .form-section textarea {
          width: 100%;
          padding: var(--spacing-s);
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          box-sizing: border-box;
        }
        .form-section textarea {
          height: 60px;
          resize: vertical;
        }
        .checkbox-section {
          display: flex;
          align-items: center;
          margin-bottom: var(--spacing-m);
        }
        .checkbox-section input[type='checkbox'] {
          margin-right: var(--spacing-s);
          width: auto;
        }
      `,
    ];
  }

  get action(): ActionInfo | undefined {
    return this.config.actions?.[this.actionId];
  }

  get actionId(): string {
    return `${this.plugin.getPluginName()}~rename-branch`;
  }

  private renderError() {
    if (!this.error) return;
    return html`<div class="error">${this.error}</div>`;
  }

  override render() {
    if (!this.action) return;
    return html`
      <h2 class="heading-2">${this.action.label}</h2>
      <gr-button
        title="${this.action.title}"
        ?disabled="${!this.action.enabled}"
        @click="${() => {
          this.error = undefined;
          this.renameBranchModal?.showModal();
        }}"
      >
        ${this.action.label}
      </gr-button>
      ${this.renderError()}
      <dialog id="renameBranchModal">
        <gr-dialog
          confirm-label="Rename Branch"
          @confirm="${this.renameBranch}"
          @cancel="${() => this.renameBranchModal?.close()}"
        >
          <div class="header" slot="header">
            Rename Branch in "${this.repoName}"
          </div>
          <div class="main" slot="main">
            <div class="form-section">
              <label for="sourceBranchInput">Source branch name:</label>
              <input
                type="text"
                id="sourceBranchInput"
                placeholder="Enter source branch name"
              />
            </div>
            
            <div class="form-section">
              <label for="destinationBranchInput">New branch name:</label>
              <input
                type="text"
                id="destinationBranchInput"
                placeholder="Enter new branch name"
              />
            </div>
            
            <div class="checkbox-section">
              <input type="checkbox" id="includeClosedChangesCheckBox" />
              <label for="includeClosedChangesCheckBox">
                Include closed changes in rename operation
              </label>
            </div>
            
            <div class="form-section">
              <label for="messageInput">Message (optional):</label>
              <textarea
                id="messageInput"
                placeholder="Optional message to be added to change messages"
              ></textarea>
            </div>
          </div>
        </gr-dialog>
      </dialog>
    `;
  }

  private renameBranch() {
    const sourceBranch = this.sourceBranchInput?.value?.trim();
    const destinationBranch = this.destinationBranchInput?.value?.trim();
    
    if (!sourceBranch) {
      this.error = 'Source branch name is required';
      return;
    }
    
    if (!destinationBranch) {
      this.error = 'Destination branch name is required';
      return;
    }

    if (sourceBranch === destinationBranch) {
      this.error = 'Source and destination branch names must be different';
      return;
    }

    if (!this.action) {
      this.error = 'rename-branch action undefined';
      this.renameBranchModal?.close();
      return;
    }
    
    if (!this.action.method) {
      this.error = 'rename-branch action does not have a HTTP method set';
      this.renameBranchModal?.close();
      return;
    }
    
    this.error = undefined;

    const endpoint = `/projects/${encodeURIComponent(this.repoName)}/${this.actionId}`;
    const requestData: RenameBranchInput = {
      source_branch: sourceBranch,
      destination_branch: destinationBranch,
      include_closed_changes: this.includeClosedChangesCheckBox?.checked ?? false,
    };

    const message = this.messageInput?.value?.trim();
    if (message) {
      requestData.message = message;
    }

    return this.plugin
      .restApi()
      .fetch(this.action.method, endpoint, requestData)
      .then(res => this.handleResponse(res))
      .catch(e => {
        this.handleError(e);
      });
  }

  private handleError(e: any) {
    if (typeof e === 'undefined') {
      this.error = 'Error renaming branch';
    } else {
      this.error = e;
    }
    this.renameBranchModal?.close();
  }

  async handleResponse(response: Response | undefined) {
    if (response?.ok) {
      this.plugin.restApi().invalidateReposCache();
      this.renameBranchModal?.close();
      // Stay on the same project page - branches will be updated
      window.location.reload();
    } else {
      this.handleError(undefined);
    }
  }
}
