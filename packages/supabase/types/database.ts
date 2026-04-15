export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  graphql_public: {
    Tables: {
      [_ in never]: never
    }
    Views: {
      [_ in never]: never
    }
    Functions: {
      graphql: {
        Args: {
          extensions?: Json
          operationName?: string
          query?: string
          variables?: Json
        }
        Returns: Json
      }
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
  public: {
    Tables: {
      card_types: {
        Row: {
          id: string
          name: string
          prompt: string
          slug: string
        }
        Insert: {
          id?: string
          name: string
          prompt: string
          slug: string
        }
        Update: {
          id?: string
          name?: string
          prompt?: string
          slug?: string
        }
        Relationships: []
      }
      collection_pebbles: {
        Row: {
          collection_id: string
          pebble_id: string
        }
        Insert: {
          collection_id: string
          pebble_id: string
        }
        Update: {
          collection_id?: string
          pebble_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "collection_pebbles_collection_id_fkey"
            columns: ["collection_id"]
            isOneToOne: false
            referencedRelation: "collections"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "collection_pebbles_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "pebbles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "collection_pebbles_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "v_pebbles_full"
            referencedColumns: ["id"]
          },
        ]
      }
      collections: {
        Row: {
          created_at: string
          id: string
          mode: string | null
          name: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          mode?: string | null
          name: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          mode?: string | null
          name?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "collections_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "collections_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      domains: {
        Row: {
          default_glyph_id: string | null
          id: string
          label: string
          name: string
          slug: string
        }
        Insert: {
          default_glyph_id?: string | null
          id?: string
          label: string
          name: string
          slug: string
        }
        Update: {
          default_glyph_id?: string | null
          id?: string
          label?: string
          name?: string
          slug?: string
        }
        Relationships: [
          {
            foreignKeyName: "domains_default_glyph_id_fkey"
            columns: ["default_glyph_id"]
            isOneToOne: false
            referencedRelation: "glyphs"
            referencedColumns: ["id"]
          },
        ]
      }
      emotions: {
        Row: {
          color: string
          id: string
          name: string
          slug: string
        }
        Insert: {
          color: string
          id?: string
          name: string
          slug: string
        }
        Update: {
          color?: string
          id?: string
          name?: string
          slug?: string
        }
        Relationships: []
      }
      glyphs: {
        Row: {
          created_at: string
          id: string
          name: string | null
          shape_id: string | null
          strokes: Json
          updated_at: string
          user_id: string | null
          view_box: string
        }
        Insert: {
          created_at?: string
          id?: string
          name?: string | null
          shape_id?: string | null
          strokes?: Json
          updated_at?: string
          user_id?: string | null
          view_box: string
        }
        Update: {
          created_at?: string
          id?: string
          name?: string | null
          shape_id?: string | null
          strokes?: Json
          updated_at?: string
          user_id?: string | null
          view_box?: string
        }
        Relationships: [
          {
            foreignKeyName: "glyphs_shape_id_fkey"
            columns: ["shape_id"]
            isOneToOne: false
            referencedRelation: "pebble_shapes"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "glyphs_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "glyphs_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      karma_events: {
        Row: {
          created_at: string
          delta: number
          id: string
          reason: string
          ref_id: string | null
          user_id: string
        }
        Insert: {
          created_at?: string
          delta: number
          id?: string
          reason: string
          ref_id?: string | null
          user_id: string
        }
        Update: {
          created_at?: string
          delta?: number
          id?: string
          reason?: string
          ref_id?: string | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "karma_events_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "karma_events_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      pebble_cards: {
        Row: {
          id: string
          pebble_id: string
          sort_order: number
          species_id: string
          value: string
        }
        Insert: {
          id?: string
          pebble_id: string
          sort_order?: number
          species_id: string
          value: string
        }
        Update: {
          id?: string
          pebble_id?: string
          sort_order?: number
          species_id?: string
          value?: string
        }
        Relationships: [
          {
            foreignKeyName: "pebble_cards_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "pebbles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_cards_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "v_pebbles_full"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_cards_species_id_fkey"
            columns: ["species_id"]
            isOneToOne: false
            referencedRelation: "card_types"
            referencedColumns: ["id"]
          },
        ]
      }
      pebble_domains: {
        Row: {
          domain_id: string
          pebble_id: string
        }
        Insert: {
          domain_id: string
          pebble_id: string
        }
        Update: {
          domain_id?: string
          pebble_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pebble_domains_domain_id_fkey"
            columns: ["domain_id"]
            isOneToOne: false
            referencedRelation: "domains"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_domains_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "pebbles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_domains_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "v_pebbles_full"
            referencedColumns: ["id"]
          },
        ]
      }
      pebble_shapes: {
        Row: {
          id: string
          name: string
          path: string
          slug: string
          view_box: string
        }
        Insert: {
          id?: string
          name: string
          path: string
          slug: string
          view_box: string
        }
        Update: {
          id?: string
          name?: string
          path?: string
          slug?: string
          view_box?: string
        }
        Relationships: []
      }
      pebble_souls: {
        Row: {
          pebble_id: string
          soul_id: string
        }
        Insert: {
          pebble_id: string
          soul_id: string
        }
        Update: {
          pebble_id?: string
          soul_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "pebble_souls_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "pebbles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_souls_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "v_pebbles_full"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebble_souls_soul_id_fkey"
            columns: ["soul_id"]
            isOneToOne: false
            referencedRelation: "souls"
            referencedColumns: ["id"]
          },
        ]
      }
      pebbles: {
        Row: {
          created_at: string
          description: string | null
          emotion_id: string
          glyph_id: string | null
          happened_at: string
          id: string
          intensity: number
          name: string
          positiveness: number
          render_manifest: Json | null
          render_svg: string | null
          render_version: string | null
          updated_at: string
          user_id: string
          visibility: string
        }
        Insert: {
          created_at?: string
          description?: string | null
          emotion_id: string
          glyph_id?: string | null
          happened_at: string
          id?: string
          intensity: number
          name: string
          positiveness: number
          render_manifest?: Json | null
          render_svg?: string | null
          render_version?: string | null
          updated_at?: string
          user_id: string
          visibility?: string
        }
        Update: {
          created_at?: string
          description?: string | null
          emotion_id?: string
          glyph_id?: string | null
          happened_at?: string
          id?: string
          intensity?: number
          name?: string
          positiveness?: number
          render_manifest?: Json | null
          render_svg?: string | null
          render_version?: string | null
          updated_at?: string
          user_id?: string
          visibility?: string
        }
        Relationships: [
          {
            foreignKeyName: "pebbles_emotion_id_fkey"
            columns: ["emotion_id"]
            isOneToOne: false
            referencedRelation: "emotions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebbles_glyph_id_fkey"
            columns: ["glyph_id"]
            isOneToOne: false
            referencedRelation: "glyphs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebbles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "pebbles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      profiles: {
        Row: {
          color_world: string
          created_at: string
          display_name: string
          id: string
          onboarding_completed: boolean
          privacy_accepted_at: string | null
          terms_accepted_at: string | null
          updated_at: string
          user_id: string
        }
        Insert: {
          color_world?: string
          created_at?: string
          display_name: string
          id?: string
          onboarding_completed?: boolean
          privacy_accepted_at?: string | null
          terms_accepted_at?: string | null
          updated_at?: string
          user_id: string
        }
        Update: {
          color_world?: string
          created_at?: string
          display_name?: string
          id?: string
          onboarding_completed?: boolean
          privacy_accepted_at?: string | null
          terms_accepted_at?: string | null
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "profiles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "profiles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: true
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      snaps: {
        Row: {
          created_at: string
          id: string
          pebble_id: string
          sort_order: number
          storage_path: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          pebble_id: string
          sort_order?: number
          storage_path: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          pebble_id?: string
          sort_order?: number
          storage_path?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "snaps_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "pebbles"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "snaps_pebble_id_fkey"
            columns: ["pebble_id"]
            isOneToOne: false
            referencedRelation: "v_pebbles_full"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "snaps_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "snaps_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
      souls: {
        Row: {
          created_at: string
          id: string
          name: string
          updated_at: string
          user_id: string
        }
        Insert: {
          created_at?: string
          id?: string
          name: string
          updated_at?: string
          user_id: string
        }
        Update: {
          created_at?: string
          id?: string
          name?: string
          updated_at?: string
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "souls_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "souls_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
    }
    Views: {
      v_bounce: {
        Row: {
          active_days: number | null
          bounce_level: number | null
          user_id: string | null
        }
        Relationships: []
      }
      v_karma_summary: {
        Row: {
          pebbles_count: number | null
          total_karma: number | null
          user_id: string | null
        }
        Insert: {
          pebbles_count?: never
          total_karma?: never
          user_id?: string | null
        }
        Update: {
          pebbles_count?: never
          total_karma?: never
          user_id?: string | null
        }
        Relationships: []
      }
      v_pebbles_full: {
        Row: {
          cards: Json | null
          collections: Json | null
          created_at: string | null
          description: string | null
          domains: Json | null
          emotion: Json | null
          emotion_id: string | null
          glyph: Json | null
          glyph_id: string | null
          happened_at: string | null
          id: string | null
          intensity: number | null
          name: string | null
          positiveness: number | null
          snaps: Json | null
          souls: Json | null
          updated_at: string | null
          user_id: string | null
          visibility: string | null
        }
        Relationships: [
          {
            foreignKeyName: "pebbles_emotion_id_fkey"
            columns: ["emotion_id"]
            isOneToOne: false
            referencedRelation: "emotions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebbles_glyph_id_fkey"
            columns: ["glyph_id"]
            isOneToOne: false
            referencedRelation: "glyphs"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "pebbles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_bounce"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "pebbles_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "v_karma_summary"
            referencedColumns: ["user_id"]
          },
        ]
      }
    }
    Functions: {
      compute_karma_delta: {
        Args: {
          p_cards_count: number
          p_description: string
          p_domains_count: number
          p_has_glyph: boolean
          p_snaps_count: number
          p_souls_count: number
        }
        Returns: number
      }
      create_pebble: { Args: { payload: Json }; Returns: string }
      delete_pebble: { Args: { p_pebble_id: string }; Returns: undefined }
      update_pebble: {
        Args: { p_pebble_id: string; payload: Json }
        Returns: undefined
      }
    }
    Enums: {
      [_ in never]: never
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  graphql_public: {
    Enums: {},
  },
  public: {
    Enums: {},
  },
} as const

